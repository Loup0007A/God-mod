package com.godmod.command;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.data.Specialization;
import com.godmod.data.Stat;
import com.godmod.data.Tier;
import com.godmod.menu.Menus;
import com.godmod.system.DivineWorld;
import com.godmod.system.FaithSystem;
import com.godmod.system.PowerActions;
import com.godmod.system.PowerSystem;
import com.godmod.system.RebellionSystem;
import com.godmod.system.Shop;
import com.godmod.system.TournamentSystem;
import com.godmod.system.WarSystem;
import com.godmod.util.Msg;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

/** Toutes les commandes du mod. Les joueurs n'ont besoin que de /dieu : le reste est accessible depuis l'interface. */
public final class GodCommands {

    private GodCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> KNOWN_PLAYERS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    private static final SuggestionProvider<CommandSourceStack> ONLINE = (ctx, b) ->
            SharedSuggestionProvider.suggest(ctx.getSource().getServer().getPlayerNames(), b);

    private static SuggestionProvider<CommandSourceStack> words(String... w) {
        return (ctx, b) -> SharedSuggestionProvider.suggest(Arrays.asList(w), b);
    }

    /** Exécute une action joueur ; la fonction renvoie un message d'erreur ou null. */
    private static int run(CommandContext<CommandSourceStack> ctx, Function<ServerPlayer, String> action) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        if (!GodData.ready()) return 0;
        String err = action.apply(player);
        if (err != null && !err.isEmpty()) {
            Msg.err(player, err);
            return 0;
        }
        return 1;
    }

    private static PlayerProfile profileArg(CommandContext<CommandSourceStack> ctx, String arg) {
        return GodData.byName(StringArgumentType.getString(ctx, arg));
    }

    private static void reply(CommandContext<CommandSourceStack> ctx, String text) {
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(text, ChatFormatting.WHITE)), false);
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerGod(d);
        registerWorld(d);
        registerShop(d);
        registerConflicts(d);
        registerAdmin(d);
    }

    // ------------------------------------------------------------------ /dieu

    private static void registerGod(CommandDispatcher<CommandSourceStack> d) {
        LiteralArgumentBuilder<CommandSourceStack> dieu = Commands.literal("dieu")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openMain(p);
                    return null;
                }))
                .then(Commands.literal("profil")
                        .executes(ctx -> run(ctx, p -> {
                            sendProfile(p, GodData.get(p), true);
                            return null;
                        }))
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    PlayerProfile me = GodData.get(p);
                                    boolean details = me.uuid.equals(t.uuid) || GodData.isAbove(me.uuid, t);
                                    sendProfile(p, t, details);
                                    return null;
                                }))))
                .then(Commands.literal("stats")
                        .executes(ctx -> run(ctx, p -> {
                            Menus.openStats(p);
                            return null;
                        }))
                        .then(Commands.argument("caracteristique", StringArgumentType.word()).suggests(words("force", "vitalite", "agilite", "esprit"))
                                .executes(ctx -> run(ctx, p -> {
                                    Stat s = Stat.parse(StringArgumentType.getString(ctx, "caracteristique"));
                                    if (s == null) return "Caractéristique inconnue : force, vitalite, agilite, esprit.";
                                    String err = PowerSystem.upgradeStat(p, s);
                                    if (err == null) Msg.ok(p, s.label + " passe au niveau " + GodData.get(p).stat(s) + ".");
                                    return err;
                                }))))
                .then(Commands.literal("specialisation")
                        .then(Commands.argument("domaine", StringArgumentType.word()).suggests(words("ocean", "ciel", "cosmos"))
                                .executes(ctx -> run(ctx, p -> {
                                    Specialization s = Specialization.parse(StringArgumentType.getString(ctx, "domaine"));
                                    if (s == null) return "Domaine inconnu : ocean, ciel ou cosmos.";
                                    return Menus.chooseSpecialization(p, s);
                                }))))
                .then(Commands.literal("choix")
                        .then(Commands.literal("trone").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, true))))
                        .then(Commands.literal("refuser").executes(ctx -> run(ctx, p -> FaithSystem.resolveThrone(p, false)))))
                .then(Commands.literal("fideles").executes(ctx -> run(ctx, p -> {
                    Menus.openFollowers(p, 0);
                    return null;
                })))
                .then(Commands.literal("liberer")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    String err = FaithSystem.releaseFollower(p, t);
                                    if (err == null) Msg.ok(p, t.name + " est libéré.");
                                    return err;
                                }))))
                .then(Commands.literal("pouvoir")
                        .executes(ctx -> run(ctx, p -> {
                            Menus.openPowers(p);
                            return null;
                        }))
                        .then(Commands.literal("benir").executes(ctx -> run(ctx, p -> {
                            PowerActions.bless(p);
                            return null;
                        })))
                        .then(Commands.literal("convoquer").executes(ctx -> run(ctx, p -> {
                            PowerActions.summonAll(p);
                            return null;
                        })))
                        .then(Commands.literal("chatier")
                                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                        .executes(ctx -> run(ctx, p -> {
                                            PlayerProfile t = profileArg(ctx, "joueur");
                                            if (t == null) return "Joueur inconnu.";
                                            PowerActions.smite(p, t);
                                            return null;
                                        }))))
                        .then(Commands.literal("temps")
                                .then(Commands.argument("mode", StringArgumentType.word()).suggests(words("jour", "nuit", "pluie", "orage", "clair"))
                                        .executes(ctx -> run(ctx, p -> {
                                            PowerActions.time(p, StringArgumentType.getString(ctx, "mode").toLowerCase());
                                            return null;
                                        }))))
                        .then(Commands.literal("espace")
                                .then(Commands.argument("joueur", StringArgumentType.word()).suggests(ONLINE)
                                        .executes(ctx -> run(ctx, p -> {
                                            ServerPlayer t = p.level().getServer().getPlayerList().getPlayerByName(StringArgumentType.getString(ctx, "joueur"));
                                            if (t == null) return "Joueur introuvable ou hors ligne.";
                                            PowerActions.space(p, t);
                                            return null;
                                        })))));
        d.register(dieu);
        d.register(Commands.literal("pantheon").executes(ctx -> run(ctx, p -> {
            Menus.openMain(p);
            return null;
        })));
        d.register(Commands.literal("classement").executes(ctx -> run(ctx, p -> {
            Menus.openRanking(p);
            return null;
        })));
    }

    private static void sendProfile(ServerPlayer viewer, PlayerProfile t, boolean details) {
        viewer.sendSystemMessage(Msg.prefix().append(Msg.text(t.name + " — ", ChatFormatting.WHITE))
                .append(Component.literal(t.tier.label).withStyle(t.tier.color)));
        viewer.sendSystemMessage(Msg.text("  Puissance : " + PowerSystem.powerScore(t) + "  ·  Fidèles : "
                + GodData.totalFollowers(t.uuid) + "  ·  Dieu : " + (t.master == null ? "aucun" : name(t.master)), ChatFormatting.GRAY));
        if (details) {
            viewer.sendSystemMessage(Msg.text("  Essence " + t.essence + " · Foi " + (int) t.faith + "/" + (int) t.maxFaith()
                    + " · " + t.money + " pièces" + (GodData.settings().payEnabled ? " · " + t.gems + " gemmes" : ""), ChatFormatting.GRAY));
            viewer.sendSystemMessage(Msg.text("  Force " + t.stat(Stat.FORCE) + " · Vitalité " + t.stat(Stat.VITALITE)
                    + " · Agilité " + t.stat(Stat.AGILITE) + " · Esprit " + t.stat(Stat.ESPRIT)
                    + " · Domaine " + t.specialization.label, ChatFormatting.GRAY));
        }
    }

    private static String name(String uuid) {
        PlayerProfile p = GodData.find(uuid);
        return p == null ? "?" : p.name;
    }

    // ------------------------------------------------------------------ /monde

    private static void registerWorld(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("monde")
                .executes(ctx -> run(ctx, p -> {
                    DivineWorld.sendHome(p);
                    return null;
                }))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, p -> {
                    DivineWorld.leave(p);
                    return null;
                })))
                .then(Commands.literal("visiter")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return "Joueur inconnu.";
                                    return DivineWorld.visit(p, t);
                                })))));
    }

    // ------------------------------------------------------------------ /shop

    private static void registerShop(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("shop")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openShop(p);
                    return null;
                }))
                .then(Commands.literal("vendre")
                        .executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), true)))
                        .then(Commands.literal("un").executes(ctx -> run(ctx, p -> Shop.sell(p, p.getMainHandItem(), false))))));
        d.register(Commands.literal("argent").executes(ctx -> run(ctx, p -> {
            PlayerProfile pr = GodData.get(p);
            Msg.info(p, "Vous avez " + pr.money + " pièces" + (GodData.settings().payEnabled ? " et " + pr.gems + " gemmes." : "."));
            return null;
        })));
    }

    // ------------------------------------------------------------------ Rébellion, tournoi, trône, guerre

    private static void registerConflicts(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("rebellion")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openRebellion(p);
                    return null;
                }))
                .then(Commands.literal("declarer").executes(ctx -> run(ctx, RebellionSystem::declare)))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, RebellionSystem::join))));

        d.register(Commands.literal("tournoi")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openTournament(p);
                    return null;
                }))
                .then(Commands.literal("rejoindre").executes(ctx -> run(ctx, TournamentSystem::join)))
                .then(Commands.literal("quitter").executes(ctx -> run(ctx, TournamentSystem::leave)))
                .then(Commands.literal("creer").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> createTournament(ctx, 1000))
                        .then(Commands.argument("prix", LongArgumentType.longArg(0))
                                .executes(ctx -> createTournament(ctx, LongArgumentType.getLong(ctx, "prix")))))
                .then(Commands.literal("annuler").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                        .executes(ctx -> {
                            String err = TournamentSystem.cancel(ctx.getSource().getServer());
                            if (err != null) ctx.getSource().sendFailure(Component.literal(err));
                            return 1;
                        })));

        d.register(Commands.literal("trone")
                .then(Commands.literal("reclamer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return "Dieu inconnu.";
                                    return TournamentSystem.claimThrone(p, god);
                                })))));

        d.register(Commands.literal("guerre")
                .executes(ctx -> run(ctx, p -> {
                    Menus.openWar(p);
                    return null;
                }))
                .then(Commands.literal("declarer")
                        .then(Commands.argument("dieu", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> run(ctx, p -> {
                                    PlayerProfile god = profileArg(ctx, "dieu");
                                    if (god == null) return "Dieu inconnu.";
                                    return WarSystem.declare(p, god);
                                })))));
    }

    private static int createTournament(CommandContext<CommandSourceStack> ctx, long prize) {
        String err = TournamentSystem.create(ctx.getSource().getServer(), prize, null);
        if (err != null) {
            ctx.getSource().sendFailure(Component.literal(err));
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ Administration

    private static void registerAdmin(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("pay").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("enable").executes(ctx -> setPay(ctx, true)))
                .then(Commands.literal("disable").executes(ctx -> setPay(ctx, false)))
                .then(Commands.literal("status").executes(ctx -> {
                    reply(ctx, "Fonctionnalités payantes : " + (GodData.settings().payEnabled ? "activées" : "désactivées"));
                    return 1;
                })));

        d.register(Commands.literal("godadmin").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("voir")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> {
                                    PlayerProfile t = profileArg(ctx, "joueur");
                                    if (t == null) return fail(ctx, "Joueur inconnu.");
                                    reply(ctx, t.name + " [" + t.uuid + "]");
                                    reply(ctx, t.tier.label + " · Essence " + t.essence + " · Foi " + (int) t.faith
                                            + " · Argent " + t.money + " · Gemmes " + t.gems);
                                    reply(ctx, "Dieu : " + (t.master == null ? "aucun" : name(t.master)) + " · Fidèles "
                                            + GodData.followersOf(t.uuid).size() + "/" + GodData.totalFollowers(t.uuid)
                                            + " · Domaine " + t.specialization.label);
                                    reply(ctx, "Stats F" + t.stat(Stat.FORCE) + " V" + t.stat(Stat.VITALITE) + " A" + t.stat(Stat.AGILITE)
                                            + " E" + t.stat(Stat.ESPRIT) + " (" + PowerSystem.spentStatPoints(t) + "/" + PowerSystem.totalStatPoints(t) + ")");
                                    reply(ctx, "Tués " + t.kills + " · Morts " + t.deaths + " · Déicides " + t.godsSlain
                                            + " · Tournois " + t.tournamentsWon + " · Jeu " + Msg.time(t.playTicks * 50)
                                            + " · Vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen));
                                    return 1;
                                })))
                .then(Commands.literal("essence")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.essence = LongArgumentType.getLong(ctx, "valeur");
                                            return "Essence de " + t.name + " = " + t.essence;
                                        })))))
                .then(Commands.literal("argent")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.money = LongArgumentType.getLong(ctx, "valeur");
                                            return "Argent de " + t.name + " = " + t.money;
                                        })))))
                .then(Commands.literal("gemmes")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("valeur", LongArgumentType.longArg(0))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            t.gems = LongArgumentType.getLong(ctx, "valeur");
                                            return "Gemmes de " + t.name + " = " + t.gems;
                                        })))))
                .then(Commands.literal("palier")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("palier", StringArgumentType.word())
                                        .suggests(words("MORTEL", "EVEILLE", "DEMI_DIEU", "DIEU", "DIEU_SUPREME"))
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            Tier tier;
                                            try {
                                                tier = Tier.valueOf(StringArgumentType.getString(ctx, "palier").toUpperCase());
                                            } catch (IllegalArgumentException e) {
                                                return "!Palier inconnu.";
                                            }
                                            PowerSystem.setTier(ctx.getSource().getServer(), t, tier, "fixé par un admin :");
                                            return "Palier de " + t.name + " = " + tier.label;
                                        })))))
                .then(Commands.literal("maitre")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .then(Commands.argument("maitre", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                        .executes(ctx -> adminEdit(ctx, t -> {
                                            String m = StringArgumentType.getString(ctx, "maitre");
                                            if (m.equalsIgnoreCase("aucun")) {
                                                t.master = null;
                                                return t.name + " est libre.";
                                            }
                                            PlayerProfile mp = GodData.byName(m);
                                            if (mp == null || mp.uuid.equals(t.uuid) || GodData.isAbove(t.uuid, mp)) return "!Maître invalide.";
                                            t.master = mp.uuid;
                                            return t.name + " est fidèle de " + mp.name + ".";
                                        })))))
                .then(Commands.literal("reset")
                        .then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN_PLAYERS)
                                .executes(ctx -> adminEdit(ctx, t -> {
                                    FaithSystem.release(ctx.getSource().getServer(), t);
                                    PlayerProfile fresh = new PlayerProfile(t.uuid, t.name);
                                    fresh.plotIndex = t.plotIndex;
                                    GodData.state().players.put(t.uuid, fresh);
                                    ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                    if (sp != null) PowerSystem.applyAttributes(sp);
                                    return "Profil de " + t.name + " réinitialisé.";
                                }))))
                .then(Commands.literal("arene").executes(ctx -> {
                    ServerPlayer p = ctx.getSource().getPlayerOrException();
                    Settings s = GodData.settings();
                    s.arenaSet = true;
                    s.arenaX = p.getX();
                    s.arenaY = p.getY();
                    s.arenaZ = p.getZ();
                    GodData.dirty();
                    reply(ctx, "Arène des tournois placée ici (les duellistes apparaissent à ±6 blocs en X).");
                    return 1;
                }))
                .then(Commands.literal("mode")
                        .then(Commands.literal("serveur").executes(ctx -> setMode(ctx, 1.0, 1.0, 72, 7)))
                        .then(Commands.literal("petit").executes(ctx -> setMode(ctx, 0.25, 2.0, 12, 2))))
                .then(Commands.literal("cooldown")
                        .then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 30)).executes(ctx -> {
                            GodData.settings().rebellionCooldownHours = IntegerArgumentType.getInteger(ctx, "heures");
                            GodData.dirty();
                            reply(ctx, "Délai entre rébellions : " + GodData.settings().rebellionCooldownHours + " h.");
                            return 1;
                        })))
                .then(Commands.literal("inactivite")
                        .then(Commands.argument("jours", IntegerArgumentType.integer(1, 365)).executes(ctx -> {
                            GodData.settings().inactiveGodDays = IntegerArgumentType.getInteger(ctx, "jours");
                            GodData.dirty();
                            reply(ctx, "Un dieu est inactif après " + GodData.settings().inactiveGodDays + " jours.");
                            return 1;
                        })))
                .then(Commands.literal("netherend")
                        .then(Commands.literal("bloquer").executes(ctx -> setNetherEnd(ctx, true)))
                        .then(Commands.literal("autoriser").executes(ctx -> setNetherEnd(ctx, false))))
                .then(Commands.literal("journal")
                        .executes(ctx -> showLog(ctx, 15))
                        .then(Commands.argument("lignes", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> showLog(ctx, IntegerArgumentType.getInteger(ctx, "lignes"))))));
    }

    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal(msg));
        return 0;
    }

    /** Modifie un profil. Si la fonction renvoie un texte commençant par « ! », c'est une erreur. */
    private static int adminEdit(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> edit) {
        PlayerProfile t = profileArg(ctx, "joueur");
        if (t == null) return fail(ctx, "Joueur inconnu (il doit s'être connecté au moins une fois).");
        String res = edit.apply(t);
        if (res.startsWith("!")) return fail(ctx, res.substring(1));
        GodData.dirty();
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : " + res);
        ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
        if (sp != null) PowerSystem.applyAttributes(sp);
        reply(ctx, res);
        return 1;
    }

    private static int setPay(CommandContext<CommandSourceStack> ctx, boolean on) {
        GodData.settings().payEnabled = on;
        GodData.dirty();
        GodData.log("ADMIN " + ctx.getSource().getTextName() + " : pay " + (on ? "enable" : "disable"));
        reply(ctx, "Fonctionnalités payantes " + (on ? "activées." : "désactivées."));
        return 1;
    }

    private static int setNetherEnd(CommandContext<CommandSourceStack> ctx, boolean block) {
        GodData.settings().disableNetherEnd = block;
        GodData.dirty();
        reply(ctx, "Nether et End " + (block ? "bloqués (items disponibles au shop)." : "accessibles."));
        return 1;
    }

    private static int setMode(CommandContext<CommandSourceStack> ctx, double threshold, double essence, int cooldown, int inactive) {
        Settings s = GodData.settings();
        s.thresholdMultiplier = threshold;
        s.essenceMultiplier = essence;
        s.rebellionCooldownHours = cooldown;
        s.inactiveGodDays = inactive;
        GodData.dirty();
        reply(ctx, "Mode appliqué : seuils ×" + threshold + ", Essence ×" + essence + ", rébellion tous les " + cooldown
                + " h, inactivité " + inactive + " j.");
        return 1;
    }

    private static int showLog(CommandContext<CommandSourceStack> ctx, int n) {
        List<String> log = GodData.state().log;
        int from = Math.max(0, log.size() - n);
        reply(ctx, "Journal (" + (log.size() - from) + " dernières entrées) :");
        for (int i = from; i < log.size(); i++) {
            String line = log.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
