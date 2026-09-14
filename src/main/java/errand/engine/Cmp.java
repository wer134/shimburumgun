package errand.engine;

/** 수치 비교 연산자. JSON 키 이름과 1:1로 대응한다. */
public enum Cmp {
    EQ("eq"), NE("ne"), GT("gt"), GTE("gte"), LT("lt"), LTE("lte");

    private final String json;

    Cmp(String json) { this.json = json; }

    public String json() { return json; }

    public boolean test(int actual, int expected) {
        return switch (this) {
            case EQ  -> actual == expected;
            case NE  -> actual != expected;
            case GT  -> actual >  expected;
            case GTE -> actual >= expected;
            case LT  -> actual <  expected;
            case LTE -> actual <= expected;
        };
    }
}
