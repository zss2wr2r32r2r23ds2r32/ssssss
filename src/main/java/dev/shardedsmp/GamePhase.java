package dev.shardedsmp;

public enum GamePhase {
    IDLE,
    PHASE_1,
    PHASE_2,
    PHASE_3,
    PHASE_4,
    PHASE_5;

    public int number() {
        return switch (this) {
            case IDLE -> 0;
            case PHASE_1 -> 1;
            case PHASE_2 -> 2;
            case PHASE_3 -> 3;
            case PHASE_4 -> 4;
            case PHASE_5 -> 5;
        };
    }

    public static GamePhase fromNumber(int number) {
        return switch (number) {
            case 1 -> PHASE_1;
            case 2 -> PHASE_2;
            case 3 -> PHASE_3;
            case 4 -> PHASE_4;
            case 5 -> PHASE_5;
            default -> IDLE;
        };
    }
}
