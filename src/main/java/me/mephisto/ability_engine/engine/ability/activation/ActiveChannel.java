package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

/**
 * A running channel (was ChannelPhase). Runs on the main-thread scheduler instead of java.util.Timer.
 * Stun/silence interruption is not checked here — the instance registry cancels the instance
 * the moment an interrupting tag is applied, and the end hook stops this ticker.
 */
final class ActiveChannel {

    private final ChannelActivation config;
    private final AbilityInstance instance;
    private final AbilityEngine engine;
    private long endsAt;
    private TaskHandle task;
    private boolean finished;
    private AbilityInstance.CastProgress bar;

    ActiveChannel(ChannelActivation config, AbilityInstance instance) {
        this.config = config;
        this.instance = instance;
        this.engine = instance.engine();
    }

    void begin() {
        instance.openBranch(); // the channel itself keeps the instance alive
        instance.onEnd(this::stopTask);
        endsAt = engine.clock().now() + config.durationTicks();
        bar = instance.showProgress(config.durationTicks());
        pulse(); // first pulse is immediate
        if (!finished && instance.isActive()) {
            task = engine.scheduler().every(config.periodTicks(), config.periodTicks(), this::pulse);
        }
    }

    private void pulse() {
        if (finished || !instance.isActive()) {
            stopTask();
            return;
        }
        if (!engine.world().isAlive(instance.caster())) {
            instance.cancel("caster_gone");
            return;
        }
        if (engine.clock().now() >= endsAt) {
            finish("duration");
            return;
        }
        String ammo = config.ammoResource();
        if (ammo != null) {
            if (!engine.resources().has(instance.caster(), ammo, config.ammoPerPulse())) {
                finish("out_of_" + ammo);
                return;
            }
            engine.resources().consume(instance.caster(), ammo, config.ammoPerPulse());
        }
        engine.runner().start(instance.newBranch());
    }

    private void finish(String reason) {
        if (finished) return;
        finished = true;
        stopTask();
        instance.clearProgress(bar);
        engine.log().debug(() -> instance + " channel finished: " + reason);
        instance.closeBranch();
    }

    private void stopTask() {
        if (task != null) task.cancel();
    }
}
