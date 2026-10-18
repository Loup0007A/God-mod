package com.godmod.system;

import com.godmod.GodMod;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.function.Consumer;

/**
 * Pose (ou efface) de grandes quantités de blocs en les étalant sur plusieurs ticks,
 * pour ne jamais bloquer le serveur (constructions du spawn, nettoyage d'îles par les admins).
 */
public final class BlockJobs {
    /** Nombre maximum d'opérations par tick. */
    public static final int BUDGET = 6000;

    public record Placement(BlockPos pos, BlockState state) {
    }

    private static final class Job {
        final ServerLevel level;
        final String name;
        final Iterator<Placement> steps;
        final Runnable onDone;
        final Consumer<String> progress;
        long done = 0;
        long lastReport = System.currentTimeMillis();

        Job(ServerLevel level, String name, Iterator<Placement> steps, Runnable onDone, Consumer<String> progress) {
            this.level = level;
            this.name = name;
            this.steps = steps;
            this.onDone = onDone;
            this.progress = progress;
        }
    }

    private static final Deque<Job> JOBS = new ArrayDeque<>();

    private BlockJobs() {
    }

    public static void submit(ServerLevel level, String name, Iterator<Placement> steps, Runnable onDone, Consumer<String> progress) {
        JOBS.add(new Job(level, name, steps, onDone, progress));
        GodMod.LOGGER.info("[God Mod] Tâche lancée : {}", name);
    }

    public static boolean busy() {
        return !JOBS.isEmpty();
    }

    public static int pending() {
        return JOBS.size();
    }

    public static void tick(MinecraftServer server) {
        int budget = BUDGET;
        while (budget > 0 && !JOBS.isEmpty()) {
            Job job = JOBS.peek();
            while (budget > 0 && job.steps.hasNext()) {
                Placement p = job.steps.next();
                BlockState current = job.level.getBlockState(p.pos());
                if (current == p.state() || (current.isAir() && p.state().isAir())) {
                    budget -= 1; // lecture seule : très peu coûteuse
                    continue;
                }
                job.level.setBlock(p.pos(), p.state(), 2);
                job.done++;
                budget -= 4;
            }
            if (!job.steps.hasNext()) {
                JOBS.poll();
                GodMod.LOGGER.info("[God Mod] Tâche terminée : {} ({} blocs modifiés)", job.name, job.done);
                if (job.onDone != null) {
                    try {
                        job.onDone.run();
                    } catch (Exception e) {
                        GodMod.LOGGER.error("[God Mod] Erreur en fin de tâche " + job.name, e);
                    }
                }
            } else if (job.progress != null && System.currentTimeMillis() - job.lastReport > 5000) {
                job.lastReport = System.currentTimeMillis();
                job.progress.accept(job.name + " : " + job.done + " blocs modifiés…");
            }
        }
    }

    /**
     * Efface une zone colonne par colonne, en ne parcourant que la partie occupée de chaque colonne
     * (au-dessus du bloc le plus haut, tout est déjà de l'air) : rapide même sur de très grandes surfaces.
     *
     * @param radius si > 0, seule la partie dans ce cercle autour du centre de la boîte est effacée.
     */
    public static Iterator<Placement> clearColumns(ServerLevel level, int x1, int z1, int x2, int z2, int y1, int y2, int radius) {
        BlockState air = Blocks.AIR.defaultBlockState();
        double cx = (x1 + x2) / 2.0, cz = (z1 + z2) / 2.0;
        return new Iterator<>() {
            int x = x1, z = z1 - 1, y = Integer.MIN_VALUE, bottom = y1;
            Placement nextP = advance();

            private boolean nextColumn() {
                while (true) {
                    z++;
                    if (z > z2) {
                        z = z1;
                        x++;
                        if (x > x2) return false;
                    }
                    if (radius > 0) {
                        double dx = x - cx, dz = z - cz;
                        if (dx * dx + dz * dz > (double) radius * radius) continue;
                    }
                    int top = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, new BlockPos(x, 0, z)).getY() - 1;
                    y = Math.min(y2, top);
                    if (y >= bottom) return true;
                }
            }

            private Placement advance() {
                if (y < bottom && !nextColumn()) return null;
                Placement p = new Placement(new BlockPos(x, y, z), air);
                y--;
                return p;
            }

            @Override
            public boolean hasNext() {
                return nextP != null;
            }

            @Override
            public Placement next() {
                Placement p = nextP;
                nextP = advance();
                return p;
            }
        };
    }
}
