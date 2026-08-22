package dev.sharded.velocitycore;

public enum ServerState {
    ONLINE("&#8AFF00&lONLINE"),
    OFFLINE("&#FF0000&lOFFLINE"),
    MAINTENANCE("&#FF0000&lMAINTEANCE");

    private final String display;

    ServerState(String display) {
        this.display = display;
    }

    public String display() {
        return display;
    }
}
