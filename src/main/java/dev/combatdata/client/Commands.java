package dev.combatdata.client;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import static dev.combatdata.core.Lang.t;

public final class Commands {
    private Commands() {
    }

    /** Turns jump reset training on or off; every start counts from zero. */
    public static void toggleTraining(Minecraft mc) {
        var s = dev.combatdata.CombatDataClient.settings();
        s.jrTraining = !s.jrTraining;
        Indicators.TRAINING.reset();
        dev.combatdata.CombatDataClient.saveSettings();
        Recorder.chat(mc, s.jrTraining ? t("Тренировка jump reset включена: прыгайте сразу после каждого удара по вам.",
                "Jump reset training on: jump right after every hit on you.", "Jump-Reset-Training an: spring direkt nach jedem Treffer auf dich.")
                : t("Тренировка jump reset выключена.", "Jump reset training off.", "Jump-Reset-Training aus."));
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> dispatcher.register(
                ClientCommandManager.literal("combatdata")
                        .executes(c -> {
                            c.getSource().sendFeedback(Component.literal(t(
                                    "Combat Data: /combatdata open меню, save сохранить, marker метка, reset сбросить буфер",
                                    "Combat Data: /combatdata open menu, save to save, marker to mark, reset to reset the buffer, training for jump reset training", "Combat Data: /combatdata open öffnet das Menü, save speichert, marker setzt eine Markierung, reset leert den Puffer, training startet das Jump-Reset-Training")));
                            return 1;
                        })
                        .then(ClientCommandManager.literal("open").executes(c -> {
                            Recorder.requestMenu();
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("marker").executes(c -> {
                            Minecraft mc = c.getSource().getClient();
                            Safe.run("команда marker", () -> Recorder.addMarker(mc));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("reset")
                                .executes(c -> {
                                    c.getSource().sendFeedback(Component.literal(t(
                                            "[Combat Data] Буфер будет удалён без сохранения. Чтобы сбросить, введите /combatdata reset confirm",
                                            "[Combat Data] The buffer will be discarded without saving. To reset, type /combatdata reset confirm", "[Combat Data] Der Puffer wird ohne Speichern verworfen. Zum Leeren tippe /combatdata reset confirm")));
                                    return 1;
                                })
                                .then(ClientCommandManager.literal("confirm").executes(c -> {
                                    Minecraft mc = c.getSource().getClient();
                                    Safe.run("команда reset", () -> Recorder.reset(mc));
                                    return 1;
                                })))
                        .then(ClientCommandManager.literal("training").executes(c -> {
                            Minecraft mc = c.getSource().getClient();
                            Safe.run("команда training", () -> toggleTraining(mc));
                            return 1;
                        }))
                        .then(ClientCommandManager.literal("save").executes(c -> {
                            Minecraft mc = c.getSource().getClient();
                            Safe.run("команда save", () -> Recorder.save(mc));
                            return 1;
                        }))));
    }
}
