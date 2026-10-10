package com.shardedcore.eventcore.event;

/** Lifecycle of a single event run. */
public enum GamePhase {

    /** Players are gathering. Nothing can be broken, placed or damaged. */
    LOBBY,

    /** A countdown is on screen; the world is still locked. */
    COUNTDOWN,

    /** The event is live: PvP, damage and building are unlocked. */
    RUNNING,

    /** A winner has been decided and {@code /end} is expected next. */
    ENDED
}
