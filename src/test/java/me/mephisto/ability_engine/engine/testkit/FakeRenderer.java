package me.mephisto.ability_engine.engine.testkit;

import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.platform.Aim;
import me.mephisto.ability_engine.engine.construct.ConstructHandle;
import me.mephisto.ability_engine.engine.construct.ConstructVisual;
import me.mephisto.ability_engine.engine.platform.ConstructRenderer;
import me.mephisto.ability_engine.engine.platform.CuePlayer;
import me.mephisto.ability_engine.engine.platform.IndicatorRenderer;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.targeting.Targeting;
import me.mephisto.ability_engine.engine.platform.ProjectileRenderer;
import me.mephisto.ability_engine.engine.platform.ProjectileVisual;
import me.mephisto.ability_engine.engine.projectile.ProjectileSpec;

import java.util.ArrayList;
import java.util.List;

public final class FakeRenderer implements ProjectileRenderer, CuePlayer, IndicatorRenderer, ConstructRenderer {

    public int spawned;
    public int alive;
    public final List<String> cues = new ArrayList<>();
    /** Looping cues currently running, by id. */
    public final List<String> loops = new ArrayList<>();

    @Override
    public me.mephisto.ability_engine.engine.platform.CueHandle start(String cueId, java.util.UUID entity) {
        loops.add(cueId);
        boolean[] stopped = {false};
        return () -> {
            if (!stopped[0]) loops.remove(cueId);
            stopped[0] = true;
        };
    }
    public int constructsAlive;
    public int previewDraws;
    public int invalidDraws;
    public final List<String> previewEnds = new ArrayList<>();

    @Override
    public void draw(java.util.UUID viewer, String abilityName, Targeting targeting, Aim from, PointTarget at, boolean valid) {
        previewDraws++;
        if (!valid) invalidDraws++;
    }

    @Override
    public void clear(java.util.UUID viewer, String reason) { previewEnds.add(reason); }

    @Override
    public ProjectileVisual spawn(String world, Vec3 position, ProjectileSpec spec) {
        spawned++;
        alive++;
        return new ProjectileVisual() {
            boolean removed;
            @Override public void moveTo(Vec3 p) {}
            @Override public void remove() {
                if (!removed) alive--;
                removed = true;
            }
        };
    }

    @Override
    public void play(String cueId, String world, Vec3 position) { cues.add(cueId); }

    /** Two-point cues drawn: {id, from, to}. */
    public final List<Object[]> lines = new ArrayList<>();

    @Override
    public void playLine(String cueId, String world, Vec3 from, Vec3 to) { lines.add(new Object[]{cueId, from, to}); }

    @Override
    public ConstructVisual spawn(ConstructHandle construct, String visual) {
        constructsAlive++;
        return new ConstructVisual() {
            boolean removed;
            @Override public void update(double progress, boolean fragile) {}
            @Override public void remove() {
                if (!removed) constructsAlive--;
                removed = true;
            }
        };
    }
}
