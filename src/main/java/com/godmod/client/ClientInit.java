package com.godmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayeredDraw;
import net.minecraftforge.event.TickEvent;

import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Partie client du mod God Civilisation (facultative pour les joueurs) :
 * remplace le chat par un panneau à onglets et se signale au serveur (/godclient) pour qu'il ne propose plus
 * l'installation. Sans le mod, le serveur reste jouable avec un Minecraft normal.
 */
public final class ClientInit {
    public static final String VERSION = "1";
    private static boolean announced = false;
    private static boolean godServer = false;

    private ClientInit() {
    }

    public static boolean onGodServer() {
        return godServer;
    }

    public static void init() {
        AddGuiOverlayLayersEvent.BUS.addListener((Consumer<AddGuiOverlayLayersEvent>) e ->
                e.getLayeredDraw().replace(ForgeLayeredDraw.POST_SLEEP_STACK, ForgeLayeredDraw.CHAT_OVERLAY, ChatHud::render));

        // Tous les messages passent par notre panneau (sauf la barre d'action au-dessus des objets).
        // Les messages du serveur arrivent par SystemMessageReceivedEvent, ceux des joueurs par ClientChatReceivedEvent(.Player).
        // On ne les retire du chat vanilla que si notre panneau est bien affiché (sinon le chat resterait vide).
        net.minecraftforge.client.event.SystemMessageReceivedEvent.BUS.addListener((Predicate<net.minecraftforge.client.event.SystemMessageReceivedEvent>) e -> {
            if (e.isOverlay() || e.getMessage() == null) return false;
            ChatStore.add(e.getMessage(), Minecraft.getInstance().gui.getGuiTicks());
            return ChatHud.active();
        });
        ClientChatReceivedEvent.Player.BUS.addListener((Predicate<ClientChatReceivedEvent.Player>) e -> {
            if (e.getMessage() == null) return false;
            ChatStore.add(e.getMessage(), Minecraft.getInstance().gui.getGuiTicks());
            return ChatHud.active();
        });
        ClientChatReceivedEvent.BUS.addListener((Predicate<ClientChatReceivedEvent>) e -> {
            if (e instanceof ClientChatReceivedEvent.Player) return false; // déjà traité ci-dessus
            if (e instanceof ClientChatReceivedEvent.System s && s.isOverlay()) return false;
            if (e.getMessage() == null) return false;
            ChatStore.add(e.getMessage(), Minecraft.getInstance().gui.getGuiTicks());
            return ChatHud.active();
        });

        ScreenEvent.MouseButtonPressed.Pre.BUS.addListener((Predicate<ScreenEvent.MouseButtonPressed.Pre>) e ->
                e.getScreen() instanceof ChatScreen cs && ChatHud.click(cs, e.getMouseX(), e.getMouseY(), e.getInfo().button()));
        ScreenEvent.MouseScrolled.Pre.BUS.addListener((Predicate<ScreenEvent.MouseScrolled.Pre>) e ->
                e.getScreen() instanceof ChatScreen && ChatHud.scroll(e.getMouseX(), e.getMouseY(), e.getDeltaY()));
        ScreenEvent.Render.Post.BUS.addListener((Consumer<ScreenEvent.Render.Post>) e -> {
            if (e.getScreen() instanceof ChatScreen) ChatHud.hover(e.getGuiGraphics(), e.getMouseX(), e.getMouseY());
        });

        ClientPlayerNetworkEvent.LoggingIn.BUS.addListener((Consumer<ClientPlayerNetworkEvent.LoggingIn>) e -> {
            announced = false;
            godServer = false;
            ChatStore.clear();
            ChatStore.select(ChatStore.Tab.TOUT);
            ChatStore.writing = ChatStore.Tab.GLOBAL;
            ChatStore.add(net.minecraft.network.chat.Component.literal("✦ Interface God Civilisation active : ouvrez le chat (T), les onglets sont en haut.")
                    .withStyle(net.minecraft.ChatFormatting.GOLD), Minecraft.getInstance().gui.getGuiTicks());
        });
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener((Consumer<ClientPlayerNetworkEvent.LoggingOut>) e -> {
            announced = false;
            godServer = false;
        });

        // Se signaler au serveur dès que son arbre de commandes contient /godclient (donc seulement sur God Civilisation).
        TickEvent.ClientTickEvent.Post.BUS.addListener((Consumer<TickEvent.ClientTickEvent.Post>) e -> {
            Minecraft mc = Minecraft.getInstance();
            if (announced || mc.player == null || mc.getConnection() == null) return;
            var root = mc.getConnection().getCommands().getRoot();
            if (root.getChild("godclient") != null) {
                announced = true;
                godServer = true;
                mc.getConnection().sendCommand("godclient " + VERSION);
            }
        });
    }
}
