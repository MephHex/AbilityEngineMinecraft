package me.mephisto.ability_engine.engine.ability.activation;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.platform.TaskHandle;

/** A running hold. Same shape as ActiveChannel, but ends on release instead of after a duration. */
final class ActiveHold {

    private final HoldActivation config;
    private final AbilityInstance instance;
    private final AbilityEngine engine;
    private long lastKeepAlive;
    private TaskHandle task;
    private boolean finished;

    ActiveHold(HoldActivation config, AbilityInstance instance) {
        this.config = config;
        this.instance = instance;
        this.engine = instance.engine();
    }

    void begin() {
        instance.openBranch(); // the hold keeps the instance alive
        instance.onEnd(this::stopTask);
        lastKeepAlive = engine.clock().now();
        instance.setKeepAlive(() -> lastKeepAlive = engine.clock().now());
        if (config.ammoResource() != null) {
            instance.showGauge(() -> engine.resources().fraction(instance.caster(), config.ammoResource()).orElse(0.0));
        }
        pulse();
        if (!finished && instance.isActive()) {
            task = engine.scheduler().every(config.periodTicks(), config.periodTicks(), this::pulse);
        }
    }

    private void pulse() {
        if (finished || !instance.isActive()) {
            stopTask();
            return;
        }
        if (engine.clock().now() - lastKeepAlive > config.releaseTicks()) {
            finish("released");
            return;
        }
        if (config.whileProjectile() != null
                && engine.projectiles().latest(instance.caster(), config.whileProjectile()).isEmpty()) {
            finish("nothing_to_hold");
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
        engine.log().debug(() -> instance + " hold finished: " + reason);
        instance.closeBranch();
    }

    private void stopTask() {
        if (task != null) task.cancel();
    }
}
