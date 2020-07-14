package com.qouteall.immersive_portals.portal.custom_portal_gen;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.Lifecycle;
import com.qouteall.immersive_portals.Helper;
import com.qouteall.immersive_portals.McHelper;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.dynamic.RegistryOps;
import net.minecraft.util.registry.RegistryTracker;
import net.minecraft.util.registry.SimpleRegistry;

public class CustomPortalGenManagement {
    private static final Multimap<Item, CustomPortalGeneration> useItemGen = HashMultimap.create();
    private static final Multimap<Item, CustomPortalGeneration> throwItemGen = HashMultimap.create();
    
    public static void onServerStarted() {
        useItemGen.clear();
        throwItemGen.clear();
        
        MinecraftServer server = McHelper.getServer();
        
        RegistryTracker.Modifiable registryTracker = new RegistryTracker.Modifiable();
        
        RegistryOps<JsonElement> registryOps =
            RegistryOps.of(JsonOps.INSTANCE,
                server.serverResourceManager.getResourceManager(),
                registryTracker
            );
        
        SimpleRegistry<CustomPortalGeneration> emptyRegistry = new SimpleRegistry<>(
            CustomPortalGeneration.registryRegistryKey,
            Lifecycle.stable()
        );
        
        DataResult<SimpleRegistry<CustomPortalGeneration>> dataResult =
            registryOps.loadToRegistry(
                emptyRegistry,
                CustomPortalGeneration.registryRegistryKey,
                CustomPortalGeneration.codec
            );
        
        SimpleRegistry<CustomPortalGeneration> result = dataResult.get().left().orElse(null);
        
        if (result == null) {
            DataResult.PartialResult<SimpleRegistry<CustomPortalGeneration>> r =
                dataResult.get().right().get();
            Helper.err("Error when parsing custom portal generation");
            Helper.err(r.message());
            return;
        }
        
        result.stream().forEach(gen -> {
            Helper.log("Loaded Custom Portal Gen " + gen.toString());
    
            load(gen);
    
            if (gen.twoWay) {
                load(gen.getReverse());
            }
        });
    }
    
    public static void load(CustomPortalGeneration gen) {
        PortalGenTrigger trigger = gen.trigger;
        if (trigger instanceof PortalGenTrigger.UseItemTrigger) {
            useItemGen.put(((PortalGenTrigger.UseItemTrigger) trigger).item, gen);
        }
        else if (trigger instanceof PortalGenTrigger.ThrowItemTrigger) {
            throwItemGen.put(
                ((PortalGenTrigger.ThrowItemTrigger) trigger).item,
                gen
            );
        }
    }
    
    public static void onItemUse(ItemUsageContext context, ActionResult actionResult) {
        if (context.getWorld().isClient()) {
            return;
        }
        for (CustomPortalGeneration gen : useItemGen.get(context.getStack().getItem())) {
            boolean result = gen.perform(
                ((ServerWorld) context.getWorld()),
                context.getBlockPos().offset(context.getSide())
            );
            if (result) {
                return;
            }
        }
    }
    
    public static void onItemTick(ItemEntity entity) {
        if (entity.world.isClient()) {
            return;
        }
        if (entity.getThrower() != null) {
            for (CustomPortalGeneration gen : throwItemGen.get(entity.getStack().getItem())) {
                boolean result = gen.perform(((ServerWorld) entity.world), entity.getBlockPos());
                if (result) {
                    entity.remove();
                    return;
                }
            }
        }
    }
}
