package io.github.envelopeautomate.mail.direct;

import io.github.envelopeautomate.ModRegistry;
import io.github.mortuusars.envelope.world.Position;
import io.github.mortuusars.envelope.world.entity.Pigeon;
import io.github.mortuusars.envelope.world.entity.PigeonVariant;
import io.github.mortuusars.envelope.world.entity.ai.CourierNavigation;
import io.github.mortuusars.envelope.world.entity.ai.goal.courier.DeliverMailGoal;
import io.github.mortuusars.envelope.world.item.component.Id;
import io.github.mortuusars.envelope.world.item.component.mail.log.DeliveryRecord;
import io.github.mortuusars.envelope.world.item.mail.Mail;
import io.github.mortuusars.envelope.world.mail.address.AddressLocation;
import io.github.mortuusars.envelope.world.mail.address.type.BlockAddress;
import io.github.mortuusars.envelope.world.mail.delivery.CourierOrigin;
import io.github.mortuusars.envelope.world.mail.delivery.Delivery;
import io.github.mortuusars.envelope.world.mail.delivery.DeliveryPhase;
import io.github.mortuusars.envelope.world.mail.delivery.DeliveryRoute;
import io.github.mortuusars.envelope.world.mail.delivery.TravelDuration;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An Envelope pigeon that carries one piece of Direct Mail straight from the sender's mailbox to the recipient's.
 * <p>
 * It reuses Envelope's {@link Delivery} and {@link DeliverMailGoal} (so flight, pathfinding, the mail backpack and
 * the drop-off rules are all Envelope's), but skips the phases that take a regular courier up to cruise height and
 * through the sorting hub:
 * <pre>
 * STARTED > APPROACHING_RECIPIENT > HANDLING_DELIVERY > FINISHED
 *                                 \ (not delivered) > APPROACHING_SENDER > HANDLING_RETURN > FINISHED
 * </pre>
 * The pigeon appears at the sender's mailbox and vanishes once the delivery ends, like Envelope's service couriers.
 * It never hands the delivery over to Envelope's background delivery; if it leaves the simulated area it finishes
 * the delivery on the spot instead.
 */
public class DirectMailPigeon extends Pigeon {
    /**
     * Longest path the pigeon looks for in one go. It re-plans whenever it reaches the end of a partial path.
     */
    private static final double PATHFINDING_RANGE = 32;
    private static final float FLYING_SPEED = 0.04f;
    private static final int MIN_FLIGHT_TICKS = 600;
    private static final int FLIGHT_TICKS_PER_BLOCK = 20;

    private int idleTicks;

    public DirectMailPigeon(EntityType<? extends DirectMailPigeon> type, Level level) {
        super(type, level);
        setCanPickUpLoot(false);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Pigeon.createAttributes().add(Attributes.FOLLOW_RANGE, PATHFINDING_RANGE);
    }

    /**
     * Spawns a pigeon in front of the sender's mailbox and sends it off with the mail.
     */
    public static DirectMailPigeon dispatch(ServerLevel level, ItemStack mail, @Nullable UUID owner,
                                            BlockAddress sender, BlockPos senderPos,
                                            BlockAddress recipient, BlockPos recipientPos) {
        DirectMailPigeon pigeon = Objects.requireNonNull(ModRegistry.DIRECT_MAIL_PIGEON.get().create(level),
              "Failed to create a Direct Mail pigeon. This should not happen.");
        pigeon.setVariant(PigeonVariant.getRandomServiceVariant(level.registryAccess(), level.getRandom()));
        pigeon.setOrigin(CourierOrigin.service());

        Vec3 spawnPos = Vec3.atBottomCenterOf(CourierNavigation.getMailboxApproachTarget(level, senderPos));
        pigeon.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.getRandom().nextFloat() * 360f, 0);
        level.addFreshEntity(pigeon);
        pigeon.onAppeared(level);

        // Straight there and back: no ascend positions away from the mailboxes, no hub.
        DeliveryRoute route = new DeliveryRoute(
              AddressLocation.exact(senderPos), AddressLocation.exact(recipientPos),
              Optional.of(senderPos), Optional.of(senderPos),
              TravelDuration.DEFAULT, Optional.empty(), TravelDuration.DEFAULT,
              Optional.of(recipientPos), Optional.of(recipientPos));

        pigeon.startDelivery(new Delivery(Id.create(level), Optional.ofNullable(owner), sender, recipient, mail,
              route, DeliveryPhase.STARTED, 0, false));
        return pigeon;
    }

    // -- AI

    @Override
    protected void registerGoals() {
        // Delivering is all this pigeon does: no wandering, eating, nesting or picking up other mail.
        goalSelector.addGoal(0, new DeliverMailGoal(this));
        goalSelector.addGoal(1, new FloatGoal(this));
    }

    @Override
    protected float getFlyingSpeed() {
        return FLYING_SPEED;
    }

    @Override
    public void aiStep() {
        super.aiStep();

        // A pigeon without a delivery has nothing to do (e.g. its delivery data failed to load).
        if (!level().isClientSide() && !isNoAi() && isAlive()) {
            idleTicks = isDelivering() ? 0 : idleTicks + 1;
            if (idleTicks > 40 && level() instanceof ServerLevel serverLevel) {
                onVanished(serverLevel);
                discard();
            }
        }
    }

    @Override
    public void checkDespawn() {
        if (level() instanceof ServerLevel level && !isNoAi() && isDelivering() && !Position.isInSimulationDistance(level, this)) {
            finishOnTheSpot(level);
            return;
        }

        super.checkDespawn();
    }

    @Override
    public boolean canMate(Animal otherAnimal) {
        return false;
    }

    // -- Delivery

    @Override
    public int getPhaseDuration(ServerLevel level, Delivery delivery, DeliveryPhase phase) {
        if (phase == DeliveryPhase.APPROACHING_RECIPIENT || phase == DeliveryPhase.APPROACHING_SENDER) {
            // Time limit for the flight. The phase completes as soon as the pigeon gets there.
            int distance = Position.getDistanceBetween(delivery.getRoute().getSenderPos(), delivery.getRoute().getRecipientPos())
                  .orElse(0);
            return Math.max(MIN_FLIGHT_TICKS, distance * FLIGHT_TICKS_PER_BLOCK);
        }
        return super.getPhaseDuration(level, delivery, phase);
    }

    @Override
    public boolean handlePhaseTransition(ServerLevel level, Delivery delivery) {
        switch (delivery.getPhase()) {
            case STARTED -> {
                delivery.beginPhase(DeliveryPhase.APPROACHING_RECIPIENT);
                return true;
            }
            case APPROACHING_RECIPIENT -> {
                if (!hasReachedSegmentEndPos(delivery)) {
                    Mail.returned(delivery.getMail(), DeliveryRecord.Message.UNABLE_TO_REACH);
                    delivery.beginPhase(DeliveryPhase.APPROACHING_SENDER);
                    return true;
                }
                return false; // HANDLING_DELIVERY
            }
            case HANDLING_DELIVERY -> {
                // Whatever is still carried was not accepted by the recipient and goes back.
                delivery.beginPhase(delivery.getMail().isEmpty() ? DeliveryPhase.FINISHED : DeliveryPhase.APPROACHING_SENDER);
                return true;
            }
            default -> {
                // APPROACHING_SENDER > HANDLING_RETURN > FINISHED. The return is handed in even when the pigeon
                // couldn't get all the way back, so returned mail isn't dropped somewhere along the route.
                return false;
            }
        }
    }

    /**
     * Completes the delivery without flying the rest of the way: hands the mail to the recipient, or failing that,
     * back to the sender. Used when the pigeon ends up somewhere entities are not simulated.
     */
    private void finishOnTheSpot(ServerLevel level) {
        Delivery delivery = getCurrentDelivery().orElse(null);
        if (delivery == null) {
            discard();
            return;
        }

        if (!delivery.getMail().isEmpty() && !delivery.getPhase().isReturning()) {
            delivery.setPhase(DeliveryPhase.HANDLING_DELIVERY, 0);
            handleMailDropOff(level, delivery, delivery.getRecipient());
        }

        if (!delivery.getMail().isEmpty()) {
            delivery.setPhase(DeliveryPhase.HANDLING_RETURN, 0);
            handleMailDropOff(level, delivery, delivery.getSender());
        }

        if (!delivery.getMail().isEmpty()) {
            Vec3 dropPos = delivery.getRoute().getSenderPos()
                  .filter(level::isLoaded)
                  .map(pos -> Vec3.atCenterOf(pos).add(0, 1, 0))
                  .orElse(position());
            Containers.dropItemStack(level, dropPos.x, dropPos.y, dropPos.z, delivery.getMail().copy());
            delivery.setMail(ItemStack.EMPTY);
        }

        delivery.end();
        setDelivery(null);
        discard();
    }
}
