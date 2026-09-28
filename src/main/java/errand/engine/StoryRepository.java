package errand.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 스토리 JSON 전체를 읽어들이고 <b>로딩 시점에 전수 검증</b>한다.
 *
 * <p>검증을 로딩 시점에 몰아넣는 것이 이 클래스의 존재 이유다. 분기가 플래그
 * 기반이 되면 "3장까지 가야 드러나는 오타"가 생기기 쉬운데, 그런 건 플레이로
 * 잡을 수 없다. 끊어진 goto, 선언되지 않은 플래그, 중복 id를 게임 시작 전에
 * 전부 잡아내고 <b>한꺼번에</b> 보고한다.
 */
public final class StoryRepository {

    private static final String BASE = "/story/";

    private final Map<String, Storylet> byId;
    private final String startId;
    private final Set<String> declaredFlags;
    private final Set<String> declaredCounters;

    private StoryRepository(Map<String, Storylet> byId, String startId,
                            Set<String> flags, Set<String> counters) {
        this.byId = byId;
        this.startId = startId;
        this.declaredFlags = flags;
        this.declaredCounters = counters;
    }

    /**
     * 기본 스토리를 읽는다.
     *
     * @param knownEnemies 도감에 있는 적 id 목록. 전투 스토리렛의 enemy를 검증하는 데
     *                     쓴다. 적 정의는 {@code errand.combat}에 있으므로 engine이
     *                     거기에 의존하지 않도록 밖에서 넣어 준다
     */
    public static StoryRepository loadDefault(Set<String> knownEnemies) {
        return load(BASE + "index.json", knownEnemies);
    }

    public static StoryRepository load(String indexResource, Set<String> knownEnemies) {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode index = read(mapper, indexResource);
        // 나머지 파일은 index.json과 같은 디렉터리에서 찾는다. 테스트가 별도
        // 디렉터리에 고장난 스토리를 두고 검증기를 시험할 수 있게 하기 위함이다.
        String dir = indexResource.substring(0, indexResource.lastIndexOf('/') + 1);

        String start = index.path("start").asText(null);
        if (start == null || start.isBlank()) {
            throw new StoryLoadException(indexResource + ": 'start' 항목이 필요합니다.");
        }

        JsonNode decl = read(mapper, dir + index.path("declarations").asText("flags.json"));
        Set<String> flags = names(decl.path("flags"));
        Set<String> counters = names(decl.path("counters"));

        Map<String, Storylet> byId = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();

        for (JsonNode fileNode : index.path("files")) {
            String file = fileNode.asText();
            JsonNode doc = read(mapper, dir + file);
            JsonNode arr = doc.path("storylets");
            if (!arr.isArray()) {
                throw new StoryLoadException(file + ": 최상위에 'storylets' 배열이 필요합니다.");
            }
            for (JsonNode n : arr) {
                Storylet s = StoryJson.storylet(n, file);
                Storylet prev = byId.put(s.id(), s);
                if (prev != null) errors.add("스토리렛 id 중복: '" + s.id() + "' (" + file + ")");
            }
        }

        errors.addAll(validate(byId, start, flags, counters, knownEnemies));
        if (!errors.isEmpty()) {
            throw new StoryLoadException("스토리 검증 실패 (%d건)%n  - %s"
                    .formatted(errors.size(), String.join("%n  - ".formatted(), errors)));
        }
        return new StoryRepository(Map.copyOf(byId), start, flags, counters);
    }

    // ---------- 검증 ----------

    private static List<String> validate(Map<String, Storylet> byId, String start,
                                         Set<String> flags, Set<String> counters,
                                         Set<String> knownEnemies) {
        List<String> errors = new ArrayList<>();

        if (!byId.containsKey(start)) {
            errors.add("start로 지정된 '" + start + "' 스토리렛이 없습니다.");
        }

        for (Storylet s : byId.values()) {
            if (s.next() != null && !byId.containsKey(s.next())) {
                errors.add("'%s'의 next가 없는 대상을 가리킵니다: '%s'".formatted(s.id(), s.next()));
            }
            for (Choice c : s.choices()) {
                if (c.target() != null && !byId.containsKey(c.target())) {
                    errors.add("'%s > %s'의 goto가 없는 대상을 가리킵니다: '%s'"
                            .formatted(s.id(), c.id(), c.target()));
                }
            }
            if (s.battle() != null) {
                BattleSpec b = s.battle();
                if (!knownEnemies.isEmpty() && !knownEnemies.contains(b.enemyId())) {
                    errors.add("'%s'가 도감에 없는 적을 가리킵니다: '%s' (가능: %s)"
                            .formatted(s.id(), b.enemyId(), String.join(", ", knownEnemies)));
                }
                if (!byId.containsKey(b.onVictory())) {
                    errors.add("'%s'의 battle.onVictory가 없는 대상을 가리킵니다: '%s'"
                            .formatted(s.id(), b.onVictory()));
                }
                if (!byId.containsKey(b.onDefeat())) {
                    errors.add("'%s'의 battle.onDefeat가 없는 대상을 가리킵니다: '%s'"
                            .formatted(s.id(), b.onDefeat()));
                }
            }
            checkNames(s, flags, counters, errors);
        }

        errors.addAll(unreachable(byId, start));
        return errors;
    }

    /** 조건과 효과가 참조하는 플래그/카운터가 flags.json에 선언돼 있는지 본다. */
    private static void checkNames(Storylet s, Set<String> flags, Set<String> counters,
                                   List<String> errors) {
        Set<String> usedFlags = new LinkedHashSet<>();
        Set<String> usedCounters = new LinkedHashSet<>();

        collectCondition(s.requires(), usedFlags, usedCounters);
        s.onEnter().forEach(e -> collectEffect(e, usedFlags, usedCounters));
        for (Choice c : s.choices()) {
            collectCondition(c.requires(), usedFlags, usedCounters);
            c.effects().forEach(e -> collectEffect(e, usedFlags, usedCounters));
        }

        for (String f : usedFlags) {
            if (!flags.contains(f)) {
                errors.add("'%s'가 선언되지 않은 플래그를 씁니다: '%s' (flags.json에 추가하세요)"
                        .formatted(s.id(), f));
            }
        }
        for (String c : usedCounters) {
            if (!counters.contains(c)) {
                errors.add("'%s'가 선언되지 않은 카운터를 씁니다: '%s' (flags.json에 추가하세요)"
                        .formatted(s.id(), c));
            }
        }
    }

    private static void collectCondition(Condition c, Set<String> flags, Set<String> counters) {
        switch (c) {
            case Condition.All a    -> a.parts().forEach(p -> collectCondition(p, flags, counters));
            case Condition.Any a    -> a.parts().forEach(p -> collectCondition(p, flags, counters));
            case Condition.Not n    -> collectCondition(n.part(), flags, counters);
            case Condition.FlagIs f -> flags.add(f.flag());
            case Condition.CounterCmp cc -> counters.add(cc.counter());
            case Condition.StatCmp ignored -> { }
            case Condition.Always ignored  -> { }
        }
    }

    private static void collectEffect(Effect e, Set<String> flags, Set<String> counters) {
        switch (e) {
            case Effect.SetFlag f    -> flags.add(f.flag());
            case Effect.AddCounter a -> counters.add(a.counter());
            case Effect.StatDelta ignored -> { }
        }
    }

    /**
     * 시작점에서 닿을 수 없는 스토리렛을 찾는다.
     *
     * <p>탐색은 명시 이동(goto/next)만 따라가는 것으로는 부족하다. 목적지를
     * 비워 둔 출구가 하나라도 있으면 거기서 <b>조건 선택</b>이 일어나므로,
     * 그 시점에 조건을 가진 스토리렛 전부가 후보로 열린다. 이것을 반영하지
     * 않으면 조건 선택으로 이어지는 구간 전체가 통째로 "닿을 수 없음"으로
     * 잘못 보고된다.
     */
    private static List<String> unreachable(Map<String, Storylet> byId, String start) {
        Set<String> seen = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>();
        if (byId.containsKey(start)) { queue.add(start); seen.add(start); }
        boolean conditionalPoolOpened = false;

        while (!queue.isEmpty()) {
            Storylet s = byId.get(queue.poll());
            if (s.isEnding()) continue;

            List<String> targets = new ArrayList<>();
            boolean hasOpenExit = false;

            if (s.battle() != null) {
                targets.add(s.battle().onVictory());
                targets.add(s.battle().onDefeat());
            } else if (s.choices().isEmpty()) {
                if (s.next() != null) targets.add(s.next());
                else hasOpenExit = true;                     // 조건 선택으로 자동 진행
            } else {
                for (Choice c : s.choices()) {
                    if (c.target() != null) targets.add(c.target());
                    else hasOpenExit = true;                 // 조건 선택으로 이동
                }
            }
            for (String t : targets) if (byId.containsKey(t) && seen.add(t)) queue.add(t);

            if (hasOpenExit && !conditionalPoolOpened) {
                conditionalPoolOpened = true;
                for (Storylet cand : byId.values()) {
                    if (!(cand.requires() instanceof Condition.Always) && seen.add(cand.id())) {
                        queue.add(cand.id());
                    }
                }
            }
        }

        List<String> errors = new ArrayList<>();
        for (Storylet s : byId.values()) {
            if (!seen.contains(s.id())) {
                errors.add("'%s'에 닿을 수 있는 경로가 없습니다 (goto 대상도 아니고 등장 조건도 없음)."
                        .formatted(s.id()));
            }
        }
        return errors;
    }

    // ---------- 조회 ----------

    public Storylet start() { return require(startId); }

    public Storylet require(String id) {
        Storylet s = byId.get(id);
        if (s == null) throw new StoryLoadException("없는 스토리렛: '" + id + "'");
        return s;
    }

    public Collection<Storylet> all()          { return byId.values(); }
    public Set<String> declaredFlags()         { return declaredFlags; }
    public Set<String> declaredCounters()      { return declaredCounters; }
    public int size()                          { return byId.size(); }

    // ---------- 파일 읽기 ----------

    private static JsonNode read(ObjectMapper mapper, String resource) {
        try (InputStream in = StoryRepository.class.getResourceAsStream(resource)) {
            if (in == null) throw new StoryLoadException("리소스를 찾을 수 없습니다: " + resource);
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new StoryLoadException(resource + " 읽기 실패: " + e.getMessage(), e);
        }
    }

    private static Set<String> names(JsonNode arr) {
        Set<String> out = new LinkedHashSet<>();
        if (arr.isArray()) for (JsonNode n : arr) out.add(n.asText());
        return Set.copyOf(out);
    }
}
