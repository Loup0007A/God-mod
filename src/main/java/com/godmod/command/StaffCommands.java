package com.godmod.command;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Role;
import com.godmod.system.AuthSystem;
import com.godmod.system.Moderation;
import com.godmod.system.PowerSystem;
import com.godmod.system.WorldBuilder;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Compte (mot de passe), modération (/mod), équipe (/staff), chat d'équipe (/sc). */
public final class StaffCommands {
    private StaffCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> KNOWN = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        if (GodData.ready()) for (PlayerProfile p : GodData.state().players.values()) names.add(p.name);
        return SharedSuggestionProvider.suggest(names, b);
    };

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        registerAccount(d);
        registerMod(d);
        registerStaff(d);
    }

    // ------------------------------------------------------------------ Compte

    private static int player(CommandContext<CommandSourceStack> ctx, Function<ServerPlayer, String> action) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        if (!GodData.ready()) return 0;
        String err = action.apply(p);
        if (err != null && !err.isEmpty()) {
            Msg.err(p, err);
            return 0;
        }
        return 1;
    }

    private static void registerAccount(CommandDispatcher<CommandSourceStack> d) {
        var register = Commands.argument("motdepasse", StringArgumentType.string())
                .then(Commands.argument("confirmation", StringArgumentType.string())
                        .executes(ctx -> player(ctx, p -> AuthSystem.register(p,
                                StringArgumentType.getString(ctx, "motdepasse"), StringArgumentType.getString(ctx, "confirmation")))));
        d.register(Commands.literal("register").then(register));
        d.register(Commands.literal("reg").then(Commands.argument("motdepasse", StringArgumentType.string())
                .then(Commands.argument("confirmation", StringArgumentType.string())
                        .executes(ctx -> player(ctx, p -> AuthSystem.register(p,
                                StringArgumentType.getString(ctx, "motdepasse"), StringArgumentType.getString(ctx, "confirmation")))))));

        d.register(Commands.literal("login").then(Commands.argument("motdepasse", StringArgumentType.greedyString())
                .executes(ctx -> player(ctx, p -> AuthSystem.login(p, StringArgumentType.getString(ctx, "motdepasse"))))));
        d.register(Commands.literal("l").then(Commands.argument("motdepasse", StringArgumentType.greedyString())
                .executes(ctx -> player(ctx, p -> AuthSystem.login(p, StringArgumentType.getString(ctx, "motdepasse"))))));

        d.register(Commands.literal("motdepasse").then(Commands.argument("ancien", StringArgumentType.string())
                .then(Commands.argument("nouveau", StringArgumentType.string())
                        .executes(ctx -> player(ctx, p -> AuthSystem.changePassword(p,
                                StringArgumentType.getString(ctx, "ancien"), StringArgumentType.getString(ctx, "nouveau")))))));
    }

    // ------------------------------------------------------------------ /mod

    private static PlayerProfile target(CommandContext<CommandSourceStack> ctx) {
        return GodData.byName(StringArgumentType.getString(ctx, "joueur"));
    }

    private static String by(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getTextName();
    }

    /** Exécute une action de modération ; un résultat commençant par « ! » est une erreur. */
    private static int act(CommandContext<CommandSourceStack> ctx, Function<PlayerProfile, String> action) {
        if (!GodData.ready()) return 0;
        PlayerProfile t = target(ctx);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("Joueur inconnu (il doit s'être connecté au moins une fois)."));
            return 0;
        }
        Role mine = Moderation.roleOf(ctx.getSource());
        Role theirs = t.role == null ? Role.JOUEUR : t.role;
        if (theirs.ordinal() > mine.ordinal()) {
            ctx.getSource().sendFailure(Component.literal("Vous ne pouvez pas sanctionner un membre de rang supérieur."));
            return 0;
        }
        String res = action.apply(t);
        if (res.startsWith("!")) {
            ctx.getSource().sendFailure(Component.literal(res.substring(1)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text(res, ChatFormatting.GREEN)), false);
        return 1;
    }

    private static String reason(CommandContext<CommandSourceStack> ctx) {
        try {
            return StringArgumentType.getString(ctx, "raison");
        } catch (IllegalArgumentException e) {
            return "Aucune raison indiquée";
        }
    }

    private static void registerMod(CommandDispatcher<CommandSourceStack> d) {
        var mod = Commands.literal("mod").requires(Moderation.require(Role.MODERATEUR))
                .executes(ctx -> help(ctx))
                .then(Commands.literal("kick").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.kick(ctx.getSource().getServer(), t, reason(ctx), by(ctx))))
                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                .executes(ctx -> act(ctx, t -> Moderation.kick(ctx.getSource().getServer(), t, reason(ctx), by(ctx)))))))
                .then(Commands.literal("mute").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 60 * 24 * 7))
                                .executes(ctx -> act(ctx, t -> Moderation.mute(ctx.getSource().getServer(), t,
                                        IntegerArgumentType.getInteger(ctx, "minutes"), reason(ctx), by(ctx))))
                                .then(Commands.argument("raison", StringArgumentType.greedyString())
                                        .executes(ctx -> act(ctx, t -> Moderation.mute(ctx.getSource().getServer(), t,
                                                IntegerArgumentType.getInteger(ctx, "minutes"), reason(ctx), by(ctx))))))))
                .then(Commands.literal("unmute").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.unmute(ctx.getSource().getServer(), t, by(ctx))))))
                .then(Commands.literal("ban").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 365))
                                .executes(ctx -> act(ctx, t -> Moderation.ban(ctx.getSource().getServer(), t,
                                        IntegerArgumentType.getInteger(ctx, "heures"), reason(ctx), by(ctx), Moderation.roleOf(ctx.getSource()))))
                                .then(Commands.argument("raison", StringArgumentType.greedyString())
                                        .executes(ctx -> act(ctx, t -> Moderation.ban(ctx.getSource().getServer(), t,
                                                IntegerArgumentType.getInteger(ctx, "heures"), reason(ctx), by(ctx), Moderation.roleOf(ctx.getSource()))))))))
                .then(Commands.literal("unban").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.unban(t, by(ctx))))))
                .then(Commands.literal("warn").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("raison", StringArgumentType.greedyString())
                                .executes(ctx -> act(ctx, t -> Moderation.warn(ctx.getSource().getServer(), t, reason(ctx), by(ctx)))))))
                .then(Commands.literal("freeze").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.toggleFreeze(ctx.getSource().getServer(), t, by(ctx))))))
                .then(Commands.literal("tp").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> {
                            ServerPlayer me = ctx.getSource().getPlayerOrException();
                            return act(ctx, t -> {
                                ServerPlayer sp = PowerSystem.online(ctx.getSource().getServer(), t.uuid);
                                if (sp == null) return "!" + t.name + " n'est pas connecté.";
                                Teleport.to(me, (ServerLevel) sp.level(), sp.getX(), sp.getY(), sp.getZ());
                                return "Téléporté auprès de " + t.name + ".";
                            });
                        })))
                .then(Commands.literal("info").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> info(ctx, t)))))
                .then(Commands.literal("journal")
                        .executes(ctx -> log(ctx, 15))
                        .then(Commands.argument("lignes", IntegerArgumentType.integer(1, 100))
                                .executes(ctx -> log(ctx, IntegerArgumentType.getInteger(ctx, "lignes")))));
        d.register(mod);

        d.register(Commands.literal("sc").requires(Moderation.require(Role.MODERATEUR))
                .then(Commands.argument("message", StringArgumentType.greedyString()).executes(ctx -> {
                    Moderation.staffChat(ctx.getSource().getServer(), by(ctx), Moderation.roleOf(ctx.getSource()),
                            StringArgumentType.getString(ctx, "message"));
                    return 1;
                })));

        // Signalement accessible à tous les joueurs.
        d.register(Commands.literal("signaler").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                .then(Commands.argument("raison", StringArgumentType.greedyString()).executes(ctx -> {
                    String who = StringArgumentType.getString(ctx, "joueur");
                    Moderation.staffAlert(ctx.getSource().getServer(), "Signalement de " + by(ctx) + " contre " + who + " : " + reason(ctx));
                    GodData.log("SIGNALEMENT : " + by(ctx) + " -> " + who + " (" + reason(ctx) + ")");
                    ctx.getSource().sendSuccess(() -> Msg.prefix().append(Msg.text("Signalement transmis à l'équipe. Merci !", ChatFormatting.GREEN)), false);
                    return 1;
                }))));
    }

    private static String info(CommandContext<CommandSourceStack> ctx, PlayerProfile t) {
        CommandSourceStack src = ctx.getSource();
        boolean online = PowerSystem.online(src.getServer(), t.uuid) != null;
        line(src, t.name + (online ? " (en ligne)" : " (hors ligne, vu il y a " + Msg.time(System.currentTimeMillis() - t.lastSeen) + ")"));
        line(src, "Rang " + (t.role == null ? "Joueur" : t.role.label) + " · " + t.tier.label + " · compte "
                + (t.passwordHash == null ? "non protégé" : "protégé") + " · IP " + (t.lastIp == null ? "?" : t.lastIp));
        if (Moderation.isBanned(t)) line(src, "BANNI : " + t.banReason + " (par " + t.bannedBy + ")");
        if (Moderation.isMuted(t)) line(src, "Muet encore " + Msg.time(t.mutedUntil - System.currentTimeMillis()));
        line(src, "Avertissements : " + t.warnings.size());
        for (int i = Math.max(0, t.warnings.size() - 5); i < t.warnings.size(); i++) line(src, "  - " + t.warnings.get(i));
        line(src, "Tués " + t.kills + " · Morts " + t.deaths + " · Argent " + t.money + " · Essence " + t.essence);
        return "Fin de la fiche de " + t.name + ".";
    }

    private static void line(CommandSourceStack src, String text) {
        src.sendSuccess(() -> Msg.text(text, ChatFormatting.GRAY), false);
    }

    private static int log(CommandContext<CommandSourceStack> ctx, int n) {
        List<String> log = GodData.state().log;
        int from = Math.max(0, log.size() - n);
        for (int i = from; i < log.size(); i++) line(ctx.getSource(), log.get(i));
        return 1;
    }

    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        line(src, "Modération : /mod kick|mute|unmute|ban|unban|warn|freeze|tp|info <joueur> · /mod journal");
        line(src, "Chat d'équipe : /sc <message> · Les modérateurs bannissent " + Moderation.MOD_MAX_BAN_HOURS + " h max (0 h = définitif, admin).");
        if (Moderation.roleOf(src).atLeast(Role.ADMIN)) {
            line(src, "Admin : /staff ajouter|retirer|liste · /godadmin ... · /pay enable|disable · /auth on|off|reset <joueur>");
        }
        return 1;
    }

    // ------------------------------------------------------------------ /staff et /auth (admins)

    private static void registerStaff(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("staff").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("ajouter").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .then(Commands.argument("rang", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("moderateur", "admin"), b))
                                .executes(ctx -> act(ctx, t -> {
                                    Role r = Role.parse(StringArgumentType.getString(ctx, "rang"));
                                    if (r == null || r == Role.JOUEUR) return "!Rang inconnu : moderateur ou admin.";
                                    return Moderation.setRole(ctx.getSource().getServer(), t, r, by(ctx));
                                })))))
                .then(Commands.literal("retirer").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> Moderation.setRole(ctx.getSource().getServer(), t, Role.JOUEUR, by(ctx))))))
                .then(Commands.literal("liste").executes(ctx -> {
                    for (PlayerProfile p : GodData.state().players.values()) {
                        if (p.role != null && p.role != Role.JOUEUR) line(ctx.getSource(), p.role.label + " : " + p.name);
                    }
                    line(ctx.getSource(), "(Les opérateurs du serveur sont aussi admins.)");
                    return 1;
                })));

        d.register(Commands.literal("auth").requires(Moderation.require(Role.ADMIN))
                .then(Commands.literal("on").executes(ctx -> setAuth(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> setAuth(ctx, false)))
                .then(Commands.literal("session").then(Commands.argument("heures", IntegerArgumentType.integer(0, 24 * 30)).executes(ctx -> {
                    GodData.settings().authSessionHours = IntegerArgumentType.getInteger(ctx, "heures");
                    GodData.dirty();
                    line(ctx.getSource(), "Reconnexion automatique depuis la même IP : " + GodData.settings().authSessionHours + " h (0 = désactivée).");
                    return 1;
                })))
                .then(Commands.literal("reset").then(Commands.argument("joueur", StringArgumentType.word()).suggests(KNOWN)
                        .executes(ctx -> act(ctx, t -> {
                            AuthSystem.reset(ctx.getSource().getServer(), t);
                            GodData.log("COMPTE : " + by(ctx) + " réinitialise le mot de passe de " + t.name);
                            return "Mot de passe de " + t.name + " réinitialisé : il devra refaire /register.";
                        })))));

        d.register(Commands.literal("structurer").requires(Moderation.require(Role.ADMIN)).executes(ctx -> {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            WorldBuilder.build(ctx.getSource().getServer(), p.blockPosition());
            line(ctx.getSource(), "Sanctuaire et arène reconstruits ici ; le spawn du serveur a été déplacé.");
            return 1;
        }));
    }

    private static int setAuth(CommandContext<CommandSourceStack> ctx, boolean on) {
        GodData.settings().authEnabled = on;
        GodData.dirty();
        GodData.log("ADMIN " + by(ctx) + " : authentification " + (on ? "activée" : "désactivée"));
        line(ctx.getSource(), "Authentification par mot de passe " + (on ? "activée" : "désactivée") + " (effet aux prochaines connexions).");
        return 1;
    }
}
