public class Pair<T1, T2> {
    private final T1 firstValue;
    private final T2 secondValue;

    public Pair(T1 firstValue, T2 secondValue) {
        this.firstValue = firstValue;
        this.secondValue = secondValue;
    }


    public T1 getFirstValue() {
        return firstValue;
    }

    public T2 getSecondValue() {
        return secondValue;
    }
}
