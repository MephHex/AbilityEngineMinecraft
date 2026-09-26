package me.mephisto.ability_engine.engine.status;

/** What happens when a status is applied to someone who already has it (LoL/Dota style). */
public enum StackPolicy {
    /** Keep whichever ends later. Standard for CC: a short stun can't shorten a long one. */
    REFRESH,
    /** Add the new duration on top of what's left. */
    EXTEND,
    /** +1 stack (up to maxStacks) and reset the duration. For "3 stacks of X" mechanics. */
    STACK
}
