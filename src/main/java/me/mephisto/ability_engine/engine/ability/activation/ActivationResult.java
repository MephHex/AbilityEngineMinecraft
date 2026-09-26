package me.mephisto.ability_engine.engine.ability.activation;

/** Outcome of trying to activate (was PolicyResult). {@code reason} is machine-readable, e.g. "on_cooldown:2.5s". */
public record ActivationResult(boolean success, String reason) {

    private static final ActivationResult OK = new ActivationResult(true, null);

    public static ActivationResult ok() { return OK; }

    /** Success, but only a targeting session opened: nothing was cast or spent yet. */
    public static ActivationResult targeting() { return new ActivationResult(true, "targeting"); }

    public boolean openedTargeting() { return success && "targeting".equals(reason); }
    public static ActivationResult fail(String reason) { return new ActivationResult(false, reason); }
}
