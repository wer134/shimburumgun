package errand.engine;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * JSON 트리를 엔진 객체로 옮긴다.
 *
 * <p>Jackson의 자동 바인딩을 쓰지 않고 손으로 매핑하는 이유는 오류 메시지 때문이다.
 * 스토리 JSON은 프로그래머가 아니라 작가가 고칠 파일이므로, "어느 파일 어느
 * 스토리렛 몇 번째 선택지에서 무엇이 틀렸는지"를 한국어로 짚어줘야 한다.
 */
final class StoryJson {

    private StoryJson() {}

    static Storylet storylet(JsonNode n, String path) {
        String id = requireText(n, "id", path);
        String at = path + " > " + id;

        if (n.has("choices") && n.has("next")) {
            throw new StoryLoadException(at + ": choices와 next를 동시에 쓸 수 없습니다. "
                    + "선택지가 있으면 각 선택지의 goto로 이동하세요.");
        }

        List<Choice> choices = new ArrayList<>();
        if (n.has("choices")) {
            JsonNode arr = requireArray(n, "choices", at);
            for (int i = 0; i < arr.size(); i++) {
                choices.add(choice(arr.get(i), at + " > choices[" + i + "]"));
            }
            if (choices.isEmpty()) {
                throw new StoryLoadException(at + ": choices가 빈 배열입니다. "
                        + "선택지가 없다면 항목 자체를 지우고 next를 쓰세요.");
            }
        }

        String scene = n.path("scene").asText(Storylet.SCENE_NARRATIVE);

        return new Storylet(
                id,
                n.path("chapter").asInt(0),
                scene,
                n.hasNonNull("speaker") ? n.get("speaker").asText() : null,
                textLines(n, at),
                condition(n.get("requires"), at + " > requires"),
                n.path("priority").asInt(0),
                n.path("once").asBoolean(true),
                effects(n.get("onEnter"), at + " > onEnter"),
                List.copyOf(choices),
                n.hasNonNull("next") ? n.get("next").asText() : null,
                n.hasNonNull("ending") ? n.get("ending").asText() : null,
                battle(n, scene, at)
        );
    }

    /** 전투 스토리렛의 {@code battle} 블록. scene과 짝이 맞는지도 여기서 본다. */
    private static BattleSpec battle(JsonNode n, String scene, String at) {
        boolean isBattleScene = Storylet.SCENE_BATTLE.equals(scene);
        JsonNode b = n.get("battle");

        if (b == null || b.isNull()) {
            if (isBattleScene) {
                throw new StoryLoadException(at + ": scene이 \"battle\"이면 battle 블록이 필요합니다. "
                        + "예: \"battle\": {\"enemy\": \"잡귀\", \"onVictory\": \"...\", \"onDefeat\": \"...\"}");
            }
            return null;
        }
        if (!isBattleScene) {
            throw new StoryLoadException(at + ": battle 블록이 있으나 scene이 \"battle\"이 아닙니다 (현재 \""
                    + scene + "\").");
        }
        if (n.hasNonNull("next")) {
            throw new StoryLoadException(at + ": 전투 스토리렛은 next를 쓰지 않습니다. "
                    + "승패에 따라 battle.onVictory / battle.onDefeat로 갈립니다.");
        }
        String bat = at + " > battle";
        return new BattleSpec(
                requireText(b, "enemy", bat),
                requireText(b, "onVictory", bat),
                requireText(b, "onDefeat", bat));
    }

    private static Choice choice(JsonNode n, String at) {
        return new Choice(
                requireText(n, "id", at),
                requireText(n, "text", at),
                condition(n.get("requires"), at + " > requires"),
                effects(n.get("effects"), at + " > effects"),
                n.hasNonNull("goto") ? n.get("goto").asText() : null
        );
    }

    // ---------- 조건 ----------

    static Condition condition(JsonNode n, String at) {
        if (n == null || n.isNull()) return new Condition.Always();
        if (!n.isObject()) throw new StoryLoadException(at + ": 조건은 객체여야 합니다.");

        if (n.has("all")) return new Condition.All(conditionList(n.get("all"), at + ".all"));
        if (n.has("any")) return new Condition.Any(conditionList(n.get("any"), at + ".any"));
        if (n.has("not")) return new Condition.Not(condition(n.get("not"), at + ".not"));

        if (n.has("flag")) {
            return new Condition.FlagIs(n.get("flag").asText(), n.path("is").asBoolean(true));
        }
        if (n.has("counter")) {
            var cmp = comparison(n, at, n.get("counter").asText());
            return new Condition.CounterCmp(n.get("counter").asText(), cmp.getKey(), cmp.getValue());
        }
        if (n.has("stat")) {
            String raw = n.get("stat").asText();
            Stat stat;
            try {
                stat = Stat.fromJson(raw);
            } catch (IllegalArgumentException e) {
                throw new StoryLoadException(at + ": " + e.getMessage());
            }
            var cmp = comparison(n, at, raw);
            return new Condition.StatCmp(stat, cmp.getKey(), cmp.getValue());
        }
        throw new StoryLoadException(at + ": 알 수 없는 조건입니다. "
                + "all/any/not/flag/counter/stat 중 하나를 쓰세요. 받은 키: " + fieldNames(n));
    }

    private static List<Condition> conditionList(JsonNode arr, String at) {
        if (!arr.isArray() || arr.isEmpty()) {
            throw new StoryLoadException(at + ": 비어 있지 않은 배열이어야 합니다.");
        }
        List<Condition> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) out.add(condition(arr.get(i), at + "[" + i + "]"));
        return List.copyOf(out);
    }

    /** gte/lte/eq/... 중 정확히 하나를 찾아 (연산자, 값) 쌍으로 돌려준다. */
    private static Map.Entry<Cmp, Integer> comparison(JsonNode n, String at, String subject) {
        Map.Entry<Cmp, Integer> found = null;
        for (Cmp c : Cmp.values()) {
            if (n.has(c.json())) {
                if (found != null) {
                    throw new StoryLoadException(at + ": '" + subject
                            + "'에 비교 연산자가 둘 이상 있습니다. 하나만 쓰세요.");
                }
                found = Map.entry(c, n.get(c.json()).asInt());
            }
        }
        if (found == null) {
            throw new StoryLoadException(at + ": '" + subject
                    + "'에 비교 연산자가 없습니다. gte/lte/eq/ne/gt/lt 중 하나가 필요합니다.");
        }
        return found;
    }

    // ---------- 효과 ----------

    static List<Effect> effects(JsonNode arr, String at) {
        if (arr == null || arr.isNull()) return List.of();
        if (!arr.isArray()) throw new StoryLoadException(at + ": 효과는 배열이어야 합니다.");
        List<Effect> out = new ArrayList<>();
        for (int i = 0; i < arr.size(); i++) out.add(effect(arr.get(i), at + "[" + i + "]"));
        return List.copyOf(out);
    }

    private static Effect effect(JsonNode n, String at) {
        if (!n.isObject()) throw new StoryLoadException(at + ": 효과는 객체여야 합니다.");

        if (n.has("flag")) {
            return new Effect.SetFlag(n.get("flag").asText(), n.path("set").asBoolean(true));
        }
        if (n.has("counter")) {
            if (!n.has("add")) {
                throw new StoryLoadException(at + ": counter 효과에는 add가 필요합니다. "
                        + "예: {\"counter\": \"천도_횟수\", \"add\": 1}");
            }
            return new Effect.AddCounter(n.get("counter").asText(), n.get("add").asInt());
        }
        for (Stat s : Stat.values()) {
            if (n.has(s.json())) return new Effect.StatDelta(s, n.get(s.json()).asInt());
        }
        throw new StoryLoadException(at + ": 알 수 없는 효과입니다. "
                + "karma/soul/hp/weapon/han/flag/counter 중 하나를 쓰세요. 받은 키: " + fieldNames(n));
    }

    // ---------- 도우미 ----------

    private static List<String> textLines(JsonNode n, String at) {
        JsonNode t = n.get("text");
        if (t == null || t.isNull()) return List.of();
        if (t.isTextual()) return List.of(t.asText());
        if (!t.isArray()) throw new StoryLoadException(at + ": text는 문자열이거나 문자열 배열이어야 합니다.");
        List<String> out = new ArrayList<>();
        for (JsonNode line : t) out.add(line.asText());
        return List.copyOf(out);
    }

    private static String requireText(JsonNode n, String field, String at) {
        if (!n.hasNonNull(field) || n.get(field).asText().isBlank()) {
            throw new StoryLoadException(at + ": '" + field + "' 항목이 없거나 비어 있습니다.");
        }
        return n.get(field).asText();
    }

    private static JsonNode requireArray(JsonNode n, String field, String at) {
        JsonNode v = n.get(field);
        if (v == null || !v.isArray()) {
            throw new StoryLoadException(at + ": '" + field + "'는 배열이어야 합니다.");
        }
        return v;
    }

    private static String fieldNames(JsonNode n) {
        List<String> names = new ArrayList<>();
        for (Iterator<String> it = n.fieldNames(); it.hasNext(); ) names.add(it.next());
        return String.join(", ", names);
    }
}
