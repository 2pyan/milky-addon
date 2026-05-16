package com.milky.hunt.modules;

import com.milky.hunt.Addon;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.packet.s2c.play.PlayerListS2CPacket;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PlayerAlarms extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgJ = settings.createGroup("Player Joining Server");
    private final SettingGroup sgRD = settings.createGroup("Player Entering Render Distance");

    // --- SETTINGS ---
    private final Setting<Boolean> renderdistance = sgGeneral.add(new BoolSetting.Builder().name("Alarm on Player in Render Distance").defaultValue(true).build());
    private final Setting<Boolean> joined = sgGeneral.add(new BoolSetting.Builder().name("Alarm on Player Joining Server").defaultValue(true).build());

    private final Setting<Boolean> useListJ = sgJ.add(new BoolSetting.Builder().name("Use Names List").defaultValue(false).visible(joined::get).build());
    private final Setting<List<String>> namesJ = sgJ.add(new StringListSetting.Builder().name("Names To Watch Out For").defaultValue(List.of("etianl", "SheepyMcGoat")).visible(() -> joined.get() && useListJ.get()).build());
    private final Setting<Integer> amountofrings = sgJ.add(new IntSetting.Builder().name("Amount Of Rings").defaultValue(5).min(1).visible(joined::get).build());
    private final Setting<Integer> ringdelay = sgJ.add(new IntSetting.Builder().name("Delay Between Rings (ticks)").defaultValue(20).min(1).visible(joined::get).build());
    private final Setting<Double> volume = sgJ.add(new DoubleSetting.Builder().name("Volume").defaultValue(1.0).sliderRange(0, 1).visible(joined::get).build());
    private final Setting<Double> pitch = sgJ.add(new DoubleSetting.Builder().name("Pitch").defaultValue(1.0).sliderRange(0.5, 2.0).visible(joined::get).build());
    private final Setting<List<SoundEvent>> soundtouse = sgJ.add(new SoundEventListSetting.Builder().name("Sound to play").defaultValue(SoundEvents.BLOCK_BELL_USE).visible(joined::get).build());

    private final Setting<Boolean> textmessage = sgRD.add(new BoolSetting.Builder().name("Text Notification").defaultValue(true).visible(renderdistance::get).build());
    private final Setting<Boolean> useListRD = sgRD.add(new BoolSetting.Builder().name("Use Names List").defaultValue(false).visible(renderdistance::get).build());
    private final Setting<List<String>> namesRD = sgRD.add(new StringListSetting.Builder().name("Names To Watch Out For").defaultValue(List.of("CookieMunscher")).visible(() -> renderdistance.get() && useListRD.get()).build());
    private final Setting<Integer> amountofringsRD = sgRD.add(new IntSetting.Builder().name("Amount Of Rings").defaultValue(2).min(1).visible(renderdistance::get).build());
    private final Setting<Integer> ringdelayRD = sgRD.add(new IntSetting.Builder().name("Delay Between Rings (ticks)").defaultValue(20).min(1).visible(renderdistance::get).build());
    private final Setting<Double> volumeRD = sgRD.add(new DoubleSetting.Builder().name("Volume").defaultValue(1.0).sliderRange(0, 1).visible(renderdistance::get).build());
    private final Setting<Double> pitchRD = sgRD.add(new DoubleSetting.Builder().name("Pitch").defaultValue(1.0).sliderRange(0.5, 2.0).visible(renderdistance::get).build());
    private final Setting<List<SoundEvent>> soundtouseRD = sgRD.add(new SoundEventListSetting.Builder().name("Sound to play").defaultValue(SoundEvents.BLOCK_ANVIL_DESTROY).visible(renderdistance::get).build());

    // --- VARIABLES ---
    private Set<String> playersSpottedRD = new HashSet<>();
    private int ticks, ringsLeft, ticksRD, ringsLeftRD;
    private boolean ringring, ringringRD;

    public PlayerAlarms() {
        super(Addon.MilkyModCategory, "PlayerAlarms", "Plays alarm sounds when a player joins or enters render distance.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WTable table = theme.table();
        WButton rst = table.add(theme.button("Reset Players Spotted")).expandX().widget();
        rst.action = () -> playersSpottedRD.clear();
        return table;
    }

    @Override
    public void onActivate() {
        playersSpottedRD.clear();
        ringring = false;
        ringringRD = false;
        ticks = 0; ticksRD = 0;
        ringsLeft = 0; ringsLeftRD = 0;
    }

    @EventHandler
    public void onPreTick(TickEvent.Pre event) {
        if (mc.world == null || mc.player == null) return;

        // Alarm: Player Joining
        if (ringring && ringsLeft > 0) {
            if (ticks <= 0) {
                playSound(soundtouse, volume, pitch);
                ticks = ringdelay.get();
                ringsLeft--;
                if (ringsLeft <= 0) ringring = false;
            } else ticks--;
        }

        // Alarm: Render Distance
        if (ringringRD && ringsLeftRD > 0) {
            if (ticksRD <= 0) {
                playSound(soundtouseRD, volumeRD, pitchRD);
                ticksRD = ringdelayRD.get();
                ringsLeftRD--;
                if (ringsLeftRD <= 0) ringringRD = false;
            } else ticksRD--;
        }

        // Check render distance entities
        if (renderdistance.get()) {
            for (Entity entity : mc.world.getEntities()) {
                if (entity instanceof PlayerEntity && entity != mc.player) {
                    String name = entity.getDisplayName().getString();
                    if (!playersSpottedRD.contains(name)) {
                        if (!useListRD.get() || namesRD.get().contains(name)) {
                            ringringRD = true;
                            ringsLeftRD = amountofringsRD.get();
                            ticksRD = 0;
                            playersSpottedRD.add(name);
                            if (textmessage.get()) error(name.replaceAll("[^a-zA-Z0-9_]", "") + " est apparu !");
                        }
                    }
                }
            }
        }
    }

    private void playSound(Setting<List<SoundEvent>> soundSetting, Setting<Double> vol, Setting<Double> pit) {
        if (mc.player == null) return;
        
        SoundEvent sound = soundSetting.get().isEmpty() 
            ? SoundEvents.BLOCK_NOTE_BLOCK_BELL 
            : soundSetting.get().get(0);

        mc.player.playSound(sound, vol.get().floatValue(), pit.get().floatValue());
    }

    @EventHandler
    private void onReceivePacket(PacketEvent.Receive event) {
        if (event.packet instanceof PlayerListS2CPacket packet && joined.get()) {
            // Vérification de l'action ADD_PLAYER de manière compatible
            if (packet.getActions().contains(PlayerListS2CPacket.Action.ADD_PLAYER)) {
                for (PlayerListS2CPacket.Entry entry : packet.getEntries()) {
                    if (entry.profile() == null) continue;
                    String name = entry.profile().getName();
                    if (!useListJ.get() || namesJ.get().contains(name)) {
                        ringring = true;
                        ringsLeft = amountofrings.get();
                        ticks = 0;
                    }
                }
            }
        }
    }
}
