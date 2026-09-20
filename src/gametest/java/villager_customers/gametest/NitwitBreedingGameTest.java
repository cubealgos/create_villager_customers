package villager_customers.gametest;

import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import villager_customers.config.VillagerCustomersConfig;

/**
 * `VC-19`: {@code villager_customers.mixin.VillagerBreedingMixin} rolls {@code nitwit_breeding_chance}
 * on {@code Villager.finalizeSpawn}'s {@code BREEDING} branch and, on success, overrides the
 * baby's profession to {@code NITWIT} instead of vanilla's unconditional {@code NONE}
 * (`docs/spec/domains/keeper.md` `KEEPER-REQ-001`, `002`;
 * `docs/spec/decisions/DEC-011-nitwit-keepers.md`).
 *
 * <p>Every scenario drives {@code Villager.finalizeSpawn} directly on a freshly constructed
 * {@code Villager} against the game test's own real {@code ServerLevel} — exercising the actual
 * mixin-hooked production method (`ARCH-DEC-007`) without needing a real breeding interaction or
 * two parent villagers, per this ticket's own Approach ("drive ... {@code finalizeSpawn} with
 * {@code EntitySpawnReason.BREEDING} directly on a mock level"). The villager is never added to the
 * level: only the resulting {@code VillagerData} is asserted, so nothing here needs to tick.
 *
 * <p>All four scenarios run inside <em>one</em> game test method rather than four, each mutating
 * and restoring {@code nitwit_breeding_chance} tightly around its own loop. A first version split
 * them into four separate {@code @GameTest} methods, each setting the config override at the top of
 * its own method body (outside {@code runAfterDelay}) and resetting it inside the delayed callback;
 * a real `just gametest` run then failed two of the four with corrupted values mid-run — the
 * "chance 0.0" scenario saw a success partway through its own loop, and the "default chance"
 * scenario saw a 100% rate — because {@code VillagerCustomersConfig}'s override is one shared
 * static field and this project's game test runner does not guarantee the four methods' bodies and
 * delayed callbacks run without interleaving each other. Folding every scenario into a single
 * method's single delayed callback removes the only other holder of that field during this test
 * class's run, which is sufficient regardless of the exact scheduling mechanism (finding recorded
 * here since it surprised this ticket's own implementation).
 */
public final class NitwitBreedingGameTest {
    private static final int SAMPLE_SIZE = 200;

    @GameTest(maxTicks = 200)
    public void theBreedingRollRespectsTheConfiguredChanceAndNeverTouchesWorldgen(GameTestHelper helper) {
        helper.runAfterDelay(3, () -> {
            ServerLevel level = helper.getLevel();

            VillagerCustomersConfig.setNitwitBreedingChanceForTesting(1.0);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                Villager baby = spawn(helper, level, EntitySpawnReason.BREEDING, new BlockPos(1, 1, 1));
                helper.assertTrue(
                    baby.getVillagerData().profession().is(VillagerProfession.NITWIT), "a bred baby is a nitwit with chance 1.0, roll " + i
                );
            }

            // KEEPER-REQ-002: a structure (worldgen) spawn is never affected by the roll, even
            // with the chance still forced to 1.0 — proving the BREEDING gate, not just luck.
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                Villager villager = spawn(helper, level, EntitySpawnReason.STRUCTURE, new BlockPos(1, 1, 1));
                helper.assertTrue(
                    !villager.getVillagerData().profession().is(VillagerProfession.NITWIT),
                    "a structure-spawned (worldgen) villager is never turned into a nitwit by this mod's roll, spawn " + i
                );
            }

            VillagerCustomersConfig.setNitwitBreedingChanceForTesting(0.0);
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                Villager baby = spawn(helper, level, EntitySpawnReason.BREEDING, new BlockPos(1, 1, 1));
                helper.assertTrue(
                    !baby.getVillagerData().profession().is(VillagerProfession.NITWIT), "a bred baby is never a nitwit with chance 0.0, roll " + i
                );
            }

            // The default chance (10%) is bounded over a large sample of real spawns against the
            // real mixin and the level's own random (`VC-19`'s Approach: "a statistical test over
            // 200 spawns with a seeded random"). A generous band (2%..25%) keeps this non-flaky
            // while still catching a grossly wrong roll; the tight distribution check itself lives
            // in `villager_customers.model.KeeperRulesTest`, which needs no game to run.
            VillagerCustomersConfig.resetNitwitBreedingChanceForTesting();
            int nitwits = 0;
            for (int i = 0; i < SAMPLE_SIZE; i++) {
                Villager baby = spawn(helper, level, EntitySpawnReason.BREEDING, new BlockPos(1, 1, 1));
                if (baby.getVillagerData().profession().is(VillagerProfession.NITWIT)) {
                    nitwits++;
                }
            }
            double rate = nitwits / (double) SAMPLE_SIZE;
            helper.assertTrue(rate > 0.02, "the default nitwit rate should be well above 2%: " + rate);
            helper.assertTrue(rate < 0.25, "the default nitwit rate should be well below 25%: " + rate);

            helper.succeed();
        });
    }

    private static Villager spawn(GameTestHelper helper, ServerLevel level, EntitySpawnReason reason, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        Villager villager = new Villager(EntityTypes.VILLAGER, level);
        villager.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        DifficultyInstance difficulty = level.getCurrentDifficultyAt(pos);
        villager.finalizeSpawn(level, difficulty, reason, null);
        return villager;
    }
}
