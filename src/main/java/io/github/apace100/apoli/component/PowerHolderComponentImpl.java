package io.github.apace100.apoli.component;

import io.github.apace100.apoli.Apoli;
import io.github.apace100.apoli.power.Power;
import io.github.apace100.apoli.power.PowerType;
import io.github.apace100.apoli.power.PowerTypeRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PowerHolderComponentImpl implements PowerHolderComponent {

    private final LivingEntity owner;
    private final ConcurrentHashMap<PowerType<?>, Power> powers = new ConcurrentHashMap<>();
    private final HashMap<PowerType<?>, List<Identifier>> powerSources = new HashMap<>();

    public PowerHolderComponentImpl(LivingEntity owner) {
        this.owner = owner;
    }

    @Override
    public boolean hasPower(PowerType<?> powerType) {
        return powers.containsKey(powerType);
    }

    @Override
    public boolean hasPower(PowerType<?> powerType, Identifier source) {
        return powerSources.containsKey(powerType) && powerSources.get(powerType).contains(source);
    }

    @Override
    public <T extends Power> T getPower(PowerType<T> powerType) {
        if(powers.containsKey(powerType)) {
            return (T)powers.get(powerType);
        }
        return null;
    }

    @Override
    public List<Power> getPowers() {
        List<Power> list = new LinkedList<>();
        list.addAll(powers.values());
        return list;
    }

    public Set<PowerType<?>> getPowerTypes() {
        Set<PowerType<?>> powerTypes = new HashSet<>();
        powerTypes.addAll(powers.keySet());
        return powerTypes;
    }

    @Override
    public <T extends Power> List<T> getPowers(Class<T> powerClass) {
        return getPowers(powerClass, false);
    }

    @Override
    public <T extends Power> List<T> getPowers(Class<T> powerClass, boolean includeInactive) {
        List<T> list = new LinkedList<>();
        for(Power power : powers.values()) {
            if(powerClass.isAssignableFrom(power.getClass()) && (includeInactive || power.isActive())) {
                list.add((T)power);
            }
        }
        return list;
    }

    public void removePower(PowerType<?> powerType, Identifier source) {
        if(powerSources.containsKey(powerType)) {
            List<Identifier> sources = powerSources.get(powerType);
            sources.remove(source);
            if(sources.isEmpty()) {
                powerSources.remove(powerType);
                Power power = powers.remove(powerType);
                if(power != null) {
                    power.onRemoved();
                    power.onLost();
                }
            }
        }
    }

    public void addPower(PowerType<?> powerType, Identifier source) {
        if(powerSources.containsKey(powerType)) {
            powerSources.get(powerType).add(source);
        } else {
            List<Identifier> sources = List.of(source);
            powerSources.put(powerType, sources);
            Power power = powerType.create(owner);
            this.powers.put(powerType, power);
            power.onAdded();
        }
    }

    @Override
    public void serverTick() {
        this.getPowers(Power.class, true).stream().filter(p -> p.shouldTick() && (p.shouldTickWhenInactive() || p.isActive())).forEach(Power::tick);
    }

    @Override
    public void readFromNbt(NbtCompound compoundTag) {
        this.fromTag(compoundTag, true);
    }

    private void fromTag(NbtCompound compoundTag, boolean callPowerOnAdd) {
        if(owner == null) {
            Apoli.LOGGER.error("Owner was null in PowerHolderComponent#fromTag!");
        }
        if(this.powers != null) {
            if(callPowerOnAdd) {
                for (Power power : powers.values()) {
                    power.onRemoved();
                    power.onLost();
                }
            }
            powers.clear();
        }

        NbtList powerList = (NbtList)compoundTag.get("Powers");
        for(int i = 0; i < powerList.size(); i++) {
            NbtCompound powerTag = powerList.getCompound(i);
            Identifier powerTypeId = Identifier.tryParse(powerTag.getString("Type"));
            try {
                PowerType<?> type = PowerTypeRegistry.get(powerTypeId);
                NbtElement data = powerTag.get("Data");
                Power power = type.create(owner);
                try {
                    power.fromTag(data);
                } catch(ClassCastException e) {
                    // Occurs when power was overriden by data pack since last world load
                    // to be a power type which uses different data class.
                    Apoli.LOGGER.warn("Data type of \"" + powerTypeId + "\" changed, skipping data for that power on entity " + owner.getName().asString());
                }
                this.powers.put(type, power);
                if(callPowerOnAdd) {
                    power.onAdded();
                }
            } catch(IllegalArgumentException e) {
                Apoli.LOGGER.warn("Power data of unregistered power \"" + powerTypeId + "\" found on entity, skipping...");
            }
        }

        this.getPowerTypes().forEach(pt -> {
            if(!this.powers.containsKey(pt)) {
                Power power = pt.create(owner);
                this.powers.put(pt, power);
            }
        });
    }

    @Override
    public void writeToNbt(NbtCompound compoundTag) {
        NbtList powerList = new NbtList();
        for(Map.Entry<PowerType<?>, Power> powerEntry : powers.entrySet()) {
            NbtCompound powerTag = new NbtCompound();
            powerTag.putString("Type", PowerTypeRegistry.getId(powerEntry.getKey()).toString());
            powerTag.put("Data", powerEntry.getValue().toTag());
            powerList.add(powerTag);
        }
        compoundTag.put("Powers", powerList);
    }

    @Override
    public void applySyncPacket(PacketByteBuf buf) {
        NbtCompound compoundTag = buf.readNbt();
        if(compoundTag != null) {
            this.fromTag(compoundTag, false);
        }
    }

    @Override
    public void sync() {
        PowerHolderComponent.sync(this.owner);
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder("PowerHolderComponent[\n");
        for (Map.Entry<PowerType<?>, Power> powerEntry : powers.entrySet()) {
            str.append("\t").append(PowerTypeRegistry.getId(powerEntry.getKey())).append(": ").append(powerEntry.getValue().toTag().toString()).append("\n");
        }
        str.append("]");
        return str.toString();
    }
}