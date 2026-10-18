package com.godmod.system;

import com.godmod.data.GodData;
import com.godmod.data.PlayerProfile;
import com.godmod.data.Settings;
import com.godmod.util.Msg;
import com.godmod.util.Teleport;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vérification par mot de passe.
 * Tant qu'un joueur n'est pas connecté (/register puis /login), il est figé, aveuglé, invulnérable,
 * et ne peut ni parler, ni utiliser de commande, ni interagir avec le monde.
 * Les mots de passe sont hachés (PBKDF2-SHA256, 120 000 itérations, sel aléatoire).
 */
public final class AuthSystem {
    public static final int MIN_LENGTH = 4;
    public static final int MAX_ATTEMPTS = 3;
    private static final int ITERATIONS = 120_000;
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Joueurs en attente de connexion, avec leur point d'ancrage. */
    private static final class Pending {
        final ServerLevel level;
        final double x, y, z;
        final long since = System.currentTimeMillis();
        int attempts = 0;
        long lastReminder = 0;

        Pending(ServerPlayer p) {
            this.level = (ServerLevel) p.level();
            this.x = p.getX();
            this.y = p.getY();
            this.z = p.getZ();
        }
    }

    private static final Map<UUID, Pending> PENDING = new HashMap<>();

    private AuthSystem() {
    }

    public static boolean isLocked(ServerPlayer player) {
        return PENDING.containsKey(player.getUUID());
    }

    // ------------------------------------------------------------------ Connexion / déconnexion

    public static void onLogin(ServerPlayer player) {
        Settings s = GodData.settings();
        PlayerProfile p = GodData.get(player);
        if (!s.authEnabled) return;
        String ip = ip(player);
        long session = s.authSessionHours * 3600_000L;
        if (p.passwordHash != null && session > 0 && ip != null && ip.equals(p.lastIp)
                && System.currentTimeMillis() - p.lastAuth < session) {
            p.lastAuth = System.currentTimeMillis();
            GodData.dirty();
            Msg.ok(player, "Reconnexion automatique (même adresse, session encore valide).");
            return;
        }
        lock(player, p);
        remind(player, p);
    }

    /**
     * Verrouille le joueur au Sanctuaire. Sa vraie position est mémorisée (et sauvegardée, au cas où il se
     * déconnecterait) pour lui être rendue après /login.
     */
    private static void lock(ServerPlayer player, PlayerProfile p) {
        p.hasAuthReturn = false;
        Hub.teleport(player);
        PENDING.put(player.getUUID(), new Pending(player));
    }

    /** Rend au joueur la position qu'il avait avant d'être verrouillé au Sanctuaire. */
    private static void restore(ServerPlayer player, PlayerProfile p) {
        if (!p.hasAuthReturn) return;
        p.hasAuthReturn = false;
        GodData.dirty();
        try {
            var key = net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.Identifier.parse(p.authReturnDim));
            ServerLevel level = player.level().getServer().getLevel(key);
            if (level != null) Teleport.to(player, level, p.authReturnX, p.authReturnY, p.authReturnZ);
        } catch (Exception e) {
            com.godmod.GodMod.LOGGER.warn("[God Mod] Position de retour invalide pour {}", p.name);
        }
    }

    public static void onLogout(ServerPlayer player) {
        PENDING.remove(player.getUUID());
    }

    private static void remind(ServerPlayer player, PlayerProfile p) {
        int t = GodData.settings().authTimeoutSeconds;
        if (p.passwordHash == null) {
            Msg.send(player, Msg.text("Bienvenue ! Protégez votre compte : ", ChatFormatting.GOLD)
                    .append(Msg.suggest("/register <motdepasse> <motdepasse>", "/register ", "Cliquez puis tapez deux fois votre mot de passe", ChatFormatting.YELLOW))
                    .append(Msg.text("  (" + t + " s)", ChatFormatting.DARK_GRAY)));
        } else {
            Msg.send(player, Msg.text("Connectez-vous : ", ChatFormatting.GOLD)
                    .append(Msg.suggest("/login <motdepasse>", "/login ", "Cliquez puis tapez votre mot de passe", ChatFormatting.YELLOW))
                    .append(Msg.text("  (" + t + " s)", ChatFormatting.DARK_GRAY)));
        }
    }

    // ------------------------------------------------------------------ Commandes

    public static String register(ServerPlayer player, String pass, String confirm) {
        PlayerProfile p = GodData.get(player);
        if (!isLocked(player) && p.passwordHash != null) return "Vous êtes déjà inscrit et connecté.";
        if (p.passwordHash != null) return "Ce compte a déjà un mot de passe : /login <motdepasse>";
        if (!pass.equals(confirm)) return "Les deux mots de passe ne correspondent pas.";
        if (pass.length() < MIN_LENGTH) return "Mot de passe trop court (" + MIN_LENGTH + " caractères minimum).";
        if (pass.equalsIgnoreCase(player.getName().getString())) return "Le mot de passe ne peut pas être votre pseudo.";
        setPassword(p, pass);
        GodData.log("COMPTE : " + p.name + " s'inscrit");
        unlock(player, p);
        Msg.ok(player, "Compte protégé ! Retenez bien votre mot de passe.");
        return null;
    }

    public static String login(ServerPlayer player, String pass) {
        PlayerProfile p = GodData.get(player);
        Pending pending = PENDING.get(player.getUUID());
        if (pending == null) return "Vous êtes déjà connecté.";
        if (p.passwordHash == null) return "Pas encore de compte : /register <motdepasse> <motdepasse>";
        if (!check(p, pass)) {
            pending.attempts++;
            GodData.log("COMPTE : mauvais mot de passe pour " + p.name + " (" + ip(player) + ")");
            if (pending.attempts >= MAX_ATTEMPTS) {
                player.connection.disconnect(Component.literal("Trop de tentatives de connexion."));
                return "";
            }
            return "Mot de passe incorrect (" + (MAX_ATTEMPTS - pending.attempts) + " essai(s) restant(s)).";
        }
        unlock(player, p);
        Msg.ok(player, "Connecté. Bon jeu !");
        return null;
    }

    public static String changePassword(ServerPlayer player, String oldPass, String newPass) {
        PlayerProfile p = GodData.get(player);
        if (isLocked(player)) return "Connectez-vous d'abord.";
        if (p.passwordHash == null) return "Vous n'avez pas de mot de passe.";
        if (!check(p, oldPass)) return "Ancien mot de passe incorrect.";
        if (newPass.length() < MIN_LENGTH) return "Nouveau mot de passe trop court (" + MIN_LENGTH + " caractères minimum).";
        setPassword(p, newPass);
        GodData.log("COMPTE : " + p.name + " change son mot de passe");
        Msg.ok(player, "Mot de passe modifié.");
        return null;
    }

    /** Réinitialisation par un admin : le joueur devra se réinscrire. */
    public static void reset(MinecraftServer server, PlayerProfile p) {
        p.passwordHash = null;
        p.passwordSalt = null;
        p.lastIp = null;
        p.lastAuth = 0;
        GodData.dirty();
        ServerPlayer sp = PowerSystem.online(server, p.uuid);
        if (sp != null) {
            lock(sp, p);
            Msg.err(sp, "Votre mot de passe a été réinitialisé par un administrateur.");
            remind(sp, p);
        }
    }

    private static void unlock(ServerPlayer player, PlayerProfile p) {
        PENDING.remove(player.getUUID());
        p.lastIp = ip(player);
        p.lastAuth = System.currentTimeMillis();
        GodData.dirty();
        player.removeEffect(MobEffects.BLINDNESS);
        player.removeEffect(MobEffects.SLOWNESS);
        player.level().getServer().getCommands().sendCommands(player);
        GameStart.afterAuth(player, p);
    }

    // ------------------------------------------------------------------ Boucle

    /** Chaque tick : maintient les joueurs non connectés sur place. */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;
        long now = System.currentTimeMillis();
        int timeout = GodData.settings().authTimeoutSeconds;
        for (ServerPlayer sp : server.getPlayerList().getPlayers().toArray(new ServerPlayer[0])) {
            Pending pd = PENDING.get(sp.getUUID());
            if (pd == null) continue;
            if (sp.level() != pd.level || Math.abs(sp.getX() - pd.x) > 0.3 || Math.abs(sp.getZ() - pd.z) > 0.3 || Math.abs(sp.getY() - pd.y) > 1.5) {
                Teleport.to(sp, pd.level, pd.x, pd.y, pd.z);
            }
            if (now - pd.lastReminder > 10_000) {
                pd.lastReminder = now;
                sp.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 260, 0, false, false));
                sp.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 260, 6, false, false));
                if (now - pd.since > 1000) remind(sp, GodData.get(sp));
            }
            if (now - pd.since > timeout * 1000L) {
                PENDING.remove(sp.getUUID());
                sp.connection.disconnect(Component.literal("Temps de connexion dépassé. Reconnectez-vous et tapez /login."));
            }
        }
    }

    /** Commandes autorisées avant connexion. */
    public static boolean allowedCommand(String command) {
        String c = command.startsWith("/") ? command.substring(1) : command;
        String root = c.split(" ", 2)[0].toLowerCase();
        return java.util.Set.of("login", "register", "l", "reg", "connexion", "inscription").contains(root);
    }

    // ------------------------------------------------------------------ Hachage

    private static void setPassword(PlayerProfile p, String pass) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        p.passwordSalt = Base64.getEncoder().encodeToString(salt);
        p.passwordHash = Base64.getEncoder().encodeToString(hash(pass, salt));
        GodData.dirty();
    }

    private static boolean check(PlayerProfile p, String pass) {
        if (p.passwordHash == null || p.passwordSalt == null) return false;
        byte[] salt = Base64.getDecoder().decode(p.passwordSalt);
        byte[] expected = Base64.getDecoder().decode(p.passwordHash);
        return MessageDigest.isEqual(expected, hash(pass, salt));
    }

    private static byte[] hash(String pass, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(pass.toCharArray(), salt, ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception e) {
            throw new IllegalStateException("PBKDF2 indisponible", e);
        }
    }

    public static String ip(ServerPlayer player) {
        SocketAddress a = player.connection.getRemoteAddress();
        if (a instanceof InetSocketAddress in && in.getAddress() != null) return in.getAddress().getHostAddress();
        return a == null ? null : a.toString();
    }
}
