package me.mephisto.ability_engine.engine.summon;

import me.mephisto.ability_engine.engine.AbilityEngine;
import me.mephisto.ability_engine.engine.ability.Ability;
import me.mephisto.ability_engine.engine.ability.AbilityInstance;
import me.mephisto.ability_engine.engine.graph.Keys;
import me.mephisto.ability_engine.engine.math.Vec3;
import me.mephisto.ability_engine.engine.target.EntityTarget;
import me.mephisto.ability_engine.engine.target.PointTarget;
import me.mephisto.ability_engine.engine.targeting.AimPoint;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.UUID;

/**
 * Second bodies (Mitosis): a clone that is a copy of its owner, not a decoy. While it lives:
 * <ul>
 *   <li>it plays their character (their kit, stats and items' stats; its own buffs and debuffs) and is on their team;</li>
 *   <li>it walks and turns as they do, from its own position and facing (Mirrors' followers);</li>
 *   <li>every cast of theirs from one of its {@code echo} slots it casts too, at the same moment, from where it stands,
 *       aimed at the spot they aim at (an echo: see {@link #prepareEcho}); its damage, kills and ultimate charge are theirs;</li>
 *   <li>it can be fused back ({@link #fuse}), swapped with ({@link #swap}), and a blow that would kill them moves them
 *       into it instead ({@link #transfer}: their consciousness moves over).</li>
 * </ul>
 * One per owner. It's a summon (summon_clone with {@code body: true}): it goes as summons go (killed, dismissed, its
 * owner dies, leaves or changes character).
 */
public final class Bodies {

    /**
     * @param body        the second body (a summon of theirs)
     * @param echoSlots   the slots whose casts it copies (e.g. primary and ability_1..ultimate; never the one that made
     *                    it)
     * @param ability     the ability that made it (it keeps running while the body lives, e.g. for the fuse)
     * @param fuseCue     played where it was when it's fused back (null = none)
     * @param transferCue played where the owner was when a killing blow moves them into it (null = none)
     */
    public record Body(UUID owner, UUID body, Set<String> echoSlots, String ability, String fuseCue, String transferCue) {}

    private final AbilityEngine engine;
    private final Map<UUID, Body> byOwner = new LinkedHashMap<>();
    private final Map<UUID, UUID> ownerOfClone = new HashMap<>();
    /** Ultimate charge (percent points) for whoever lands a blow that a transfer turns away (a kill that wasn't). */
    private double transferCharge = 5;

    public Bodies(AbilityEngine engine) {
        this.engine = engine;
        engine.summons().addRemovalListener((owner, entity, cue) -> removed(entity));
    }

    public void setTransferCharge(double percent) { this.transferCharge = Math.max(0, percent); }

    /** {@code clone} (a summon of {@code owner}'s, just spawned) is their second body from now on. */
    public void add(Body body) {
        Body old = byOwner.put(body.owner(), body);
        if (old != null && !old.body().equals(body.body())) {
            ownerOfClone.remove(old.body());
            engine.loadouts().dropStandIn(old.body());
        }
        ownerOfClone.put(body.body(), body.owner());
        engine.loadouts().standIn(body.body(), body.owner());
        engine.mirrors().addFollower(body.owner(), body.body());
    }

    /** {@code owner}'s second body, while it's alive. */
    public Optional<UUID> cloneOf(UUID owner) {
        Body b = byOwner.get(owner);
        if (b == null || !engine.world().isAlive(b.body()) || !engine.summons().isSummon(b.body())) return Optional.empty();
        return Optional.of(b.body());
    }

    public Optional<Body> bodyOf(UUID owner) { return Optional.ofNullable(byOwner.get(owner)); }

    /** Whose second body this is, if it is one. */
    public Optional<UUID> ownerOf(UUID clone) { return Optional.ofNullable(ownerOfClone.get(clone)); }

    public boolean isBody(UUID entity) { return ownerOfClone.containsKey(entity); }

    /** Who gets the credit for what {@code entity} does: a second body's owner, else itself. */
    public UUID credited(UUID entity) {
        if (entity == null) return null;
        UUID owner = ownerOfClone.get(entity);
        return owner != null ? owner : entity;
    }

    /** {@code owner}'s second body's health as a share of its max HP (empty: no body). */
    public OptionalDouble cloneHealthShare(UUID owner) {
        return cloneOf(owner).map(c -> engine.world().healthFraction(c)).orElse(OptionalDouble.empty());
    }

    /** Health right now in design HP (max HP x share). */
    private double health(UUID entity) {
        return engine.stats().maxHealth(entity) * engine.world().healthFraction(entity).orElse(1);
    }

    // ---- echoes --------------------------------------------------------------------------------------------------

    /**
     * {@code owner} is casting {@code ability} from a slot: their second body's copy of that cast, ready to start (not
     * started), or null if it doesn't copy it (no body, not one of its slots, or crowd control on the body keeps it from
     * that slot: stunned, silenced, disarmed, rooted for a movement ability, its blocked_by). It's cast from the body's
     * own position, aimed at the spot they're aiming at (their lines of sight cross there), with the same values as theirs (the slot...) but no target of theirs. No costs, no cooldown
     * of its own (they always cast together).
     */
    public AbilityInstance prepareEcho(UUID owner, Ability ability, Map<String, Object> presets) {
        Body body = byOwner.get(owner);
        if (body == null || cloneOf(owner).isEmpty()) return null;
        if (!(presets.get(Keys.SLOT.name()) instanceof String slot) || !body.echoSlots().contains(slot)) return null;
        UUID clone = body.body();
        if (engine.loadouts().crowdControl(clone, slot).isPresent()) return null;
        engine.mirrors().faceFocus(owner, clone); // aimed at what they aim at, right now
        Map<String, Object> own = new HashMap<>(presets);
        own.remove(Keys.TARGET.name());
        own.remove(Keys.AIM.name());
        AimPoint.resolve(engine, clone, ability.targeting()).ifPresent(aim -> own.put(Keys.AIM.name(), aim));
        AbilityInstance echo = new AbilityInstance(engine, ability, clone, own);
        echo.deferCooldownManually();
        return echo;
    }

    // ---- fuse, swap, transfer -------------------------------------------------------------------------------------

    /**
     * Fuse {@code owner}'s second body back into them: its health is added to theirs (up to their max HP: the rest is
     * lost), every buff and debuff on it moves over to them with the time it has left (where they have the same one, the
     * stronger stays), and it's gone. False if there's no body.
     */
    public boolean fuse(UUID owner) {
        Optional<UUID> found = cloneOf(owner);
        if (found.isEmpty()) return false;
        UUID clone = found.get();
        double max = engine.stats().maxHealth(owner);
        double total = Math.min(max, health(owner) + health(clone));
        if (max > 0) engine.movement().setHealthShare(owner, total / max);
        engine.statuses().moveAll(clone, owner);
        playAt(owner, clone, byOwner.get(owner).fuseCue());
        engine.summons().dismissEntity(clone, false);
        return true;
    }

    /**
     * {@code owner} and their second body trade places: positions and facing. Everything else stays with the body it's
     * on: they take over the body's health, buffs and debuffs, and the body they left keeps theirs (a stunned body stays
     * stunned, now as the clone). False if there's no body.
     */
    public boolean swap(UUID owner) {
        Optional<UUID> found = cloneOf(owner);
        if (found.isEmpty()) return false;
        UUID clone = found.get();
        var world = engine.world();
        Optional<PointTarget> a = world.positionOf(new EntityTarget(owner)), b = world.positionOf(new EntityTarget(clone));
        if (a.isEmpty() || b.isEmpty() || !a.get().world().equals(b.get().world())) return false;
        Vec3 lookA = world.aimOf(owner).map(x -> x.direction()).orElse(null);
        Vec3 lookB = world.aimOf(clone).map(x -> x.direction()).orElse(null);
        double shareA = world.healthFraction(owner).orElse(1), shareB = world.healthFraction(clone).orElse(1);
        var move = engine.movement();
        move.swapPlaces(owner, clone, a.get().position(), b.get().position(), lookA, lookB);
        move.stop(owner);
        move.stop(clone);
        safeLanding(owner);
        safeLanding(clone);
        move.setHealthShare(owner, shareB);
        move.setHealthShare(clone, shareA);
        engine.statuses().swapAll(owner, clone);
        engine.mirrors().resync(owner); // (their jump in position and look isn't a step or a turn to copy)
        return true;
    }

    /**
     * A blow is about to kill {@code owner} while their second body lives: their consciousness moves over instead. They're
     * put where the body is, facing its way, with its health and only its buffs and debuffs (the dying body's are gone),
     * and the body is gone; their casts end (except the one that made the body, which sees it go). {@code attacker}
     * (whoever landed the blow) gets a little ultimate charge, as for an assist. Returns the share of max HP to leave them
     * at; empty = no body (they die).
     */
    public OptionalDouble transfer(UUID owner, UUID attacker) {
        Optional<UUID> found = cloneOf(owner);
        if (found.isEmpty()) return OptionalDouble.empty();
        UUID clone = found.get();
        Body body = byOwner.get(owner);
        var world = engine.world();
        Optional<PointTarget> there = world.positionOf(new EntityTarget(clone));
        Optional<PointTarget> was = world.positionOf(new EntityTarget(owner));
        if (there.isEmpty()) return OptionalDouble.empty();
        double share = Math.max(0.01, world.healthFraction(clone).orElse(1));
        Vec3 look = world.aimOf(clone).map(x -> x.direction()).orElse(null);
        for (AbilityInstance running : List.copyOf(engine.instances().of(owner))) {
            if (!running.ability().id().equals(body.ability())) running.cancel("transfer"); // e.g. the ultimate ends
        }
        engine.targeting().cancel(owner, "transfer");
        engine.statuses().clearMovable(owner);
        engine.statuses().moveAll(clone, owner);
        engine.movement().putAt(owner, clone, there.get().position(), look);
        engine.movement().stop(owner);
        safeLanding(owner);
        engine.mirrors().resync(owner);
        if (was.isPresent() && body.transferCue() != null) {
            engine.cuesFor(owner).play(body.transferCue(), was.get().world(), was.get().position());
        }
        UUID credit = credited(attacker);
        if (credit != null && !credit.equals(owner)) engine.ultCharge().add(credit, transferCharge);
        engine.summons().dismissEntity(clone, false);
        return OptionalDouble.of(share);
    }

    /** Just put somewhere new: a moment of no suffocation (a body a little in a wall or the floor gets out of it). */
    private void safeLanding(UUID entity) {
        engine.tags().grant(entity, me.mephisto.ability_engine.engine.tag.Tags.SWAPPING);
        engine.scheduler().after(10, () -> engine.tags().revoke(entity, me.mephisto.ability_engine.engine.tag.Tags.SWAPPING));
    }

    private void playAt(UUID owner, UUID at, String cue) {
        if (cue == null) return;
        engine.world().positionOf(new EntityTarget(at))
                .ifPresent(p -> engine.cuesFor(owner).play(cue, p.world(), p.position()));
    }

    /** A summon went: if it was a second body, it's no one's any more, and whatever it was casting ends. */
    private void removed(UUID entity) {
        UUID owner = ownerOfClone.remove(entity);
        if (owner == null) return;
        Body b = byOwner.get(owner);
        if (b != null && b.body().equals(entity)) byOwner.remove(owner);
        engine.loadouts().dropStandIn(entity);
        engine.instances().cancelAll(entity, "body_gone");
        engine.links().breakAll(entity);
        engine.statuses().clear(entity);
        engine.tags().clear(entity);
    }

    /** Plugin disable. */
    public void shutdown() {
        for (UUID clone : List.copyOf(ownerOfClone.keySet())) engine.loadouts().dropStandIn(clone);
        byOwner.clear();
        ownerOfClone.clear();
    }
}
