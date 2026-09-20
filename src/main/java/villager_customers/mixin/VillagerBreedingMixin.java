package villager_customers.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import villager_customers.config.VillagerCustomersConfig;
import villager_customers.model.KeeperRules;

/**
 * Rolls the nitwit breeding chance (`docs/spec/domains/keeper.md` `KEEPER-REQ-001`, `002`;
 * `docs/spec/decisions/DEC-011-nitwit-keepers.md`; `docs/spec/04-architecture.md` `ARCH-DEC-007`).
 *
 * <p>{@code javap -p -c} against {@code Villager.finalizeSpawn(ServerLevelAccessor,
 * DifficultyInstance, EntitySpawnReason, SpawnGroupData)} in the 26.2 jar
 * (`minecraft-merged-deobf-26.2.jar`) shows exactly one {@code areturn}, so a single
 * {@code @Inject} at {@code RETURN} runs once, after vanilla's own {@code BREEDING} branch has
 * already unconditionally set the baby's profession to {@code NONE} (via the public
 * {@code setVillagerData}) and after the {@code super.finalizeSpawn} call has already run. This
 * mixin then re-checks the spawn reason itself, rolls {@code nitwit_breeding_chance} with the
 * level's own random ({@code ServerLevelAccessor.getRandom()}, inherited from
 * {@code LevelAccessor}), and on success overrides the profession to {@code NITWIT} — keeping the
 * villager's type and level untouched, since {@code VillagerData.withProfession} only replaces the
 * profession field. {@code EntitySpawnReason.STRUCTURE} (worldgen) takes a different branch
 * entirely (setting {@code assignProfessionWhenSpawned}, never touching profession directly) and
 * every other spawn reason takes neither branch, so gating on {@code BREEDING} here leaves both
 * untouched (`KEEPER-REQ-002`).
 *
 * <p>{@code Villager.setVillagerData} is {@code public} and, confirmed by disassembling it, does
 * <em>not</em> call {@code refreshBrain} itself — it only clears cached offers when the profession
 * actually changed and writes the new {@code VillagerData} to synced entity data. Vanilla's own
 * {@code BREEDING} branch never needs a refresh because the profession it sets ({@code NONE})
 * already matches what the brain was built with at construction time. This mixin's own profession
 * change happens after that brain already exists, so it calls {@code refreshBrain} explicitly —
 * cheap (`docs/spec/domains/keeper.md` "Constraints and prior findings") and consistent with the
 * research's own conversion-item option, which calls it "since work-package goals differ by
 * profession, even though a nitwit's own work package is trivial" (research §G.3(b)). Finding
 * recorded either way per this ticket's Approach: the call is made.
 */
@Mixin(Villager.class)
final class VillagerBreedingMixin {
    @Inject(method = "finalizeSpawn", at = @At("RETURN"))
    private void villager_customers$rollNitwitOnBreeding(
        ServerLevelAccessor levelAccessor, DifficultyInstance difficulty, EntitySpawnReason spawnReason, SpawnGroupData spawnGroupData,
        CallbackInfoReturnable<SpawnGroupData> cir
    ) {
        if (spawnReason != EntitySpawnReason.BREEDING) {
            return;
        }
        double chance = VillagerCustomersConfig.nitwitBreedingChance();
        if (!KeeperRules.rollsNitwit(levelAccessor.getRandom().nextDouble(), chance)) {
            return;
        }
        Villager villager = (Villager) (Object) this;
        villager.setVillagerData(villager.getVillagerData().withProfession(levelAccessor.registryAccess(), VillagerProfession.NITWIT));
        if (levelAccessor instanceof ServerLevel serverLevel) {
            villager.refreshBrain(serverLevel);
        }
    }
}
