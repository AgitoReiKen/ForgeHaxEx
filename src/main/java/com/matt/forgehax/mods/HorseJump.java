package com.matt.forgehax.mods;

import com.matt.forgehax.Wrapper;
import com.matt.forgehax.asm.reflection.FastReflection;
import com.matt.forgehax.events.LocalPlayerUpdateEvent;
import com.matt.forgehax.util.mod.ToggleMod;
import com.matt.forgehax.util.mod.loader.RegisterMod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@RegisterMod
public class HorseJump extends ToggleMod {
    public HorseJump() {
        super("HorseJump", false, "always max horse jump");
    }


    @SubscribeEvent
	public void onLocalPlayerUpdate(LocalPlayerUpdateEvent event) {
        FastReflection.Fields.EntityPlayerSP_horseJumpPower.set(Wrapper.getLocalPlayer(), 1.F);
    }
}