package io.github.apace100.apoli.power;

import io.github.apace100.apoli.component.PowerHolderComponent;
import io.github.apace100.apoli.power.factory.PowerFactory;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class PowerType<T extends Power> {

    private Identifier identifier;
    private PowerFactory<T>.Instance factory;
    private boolean isHidden = false;

    private String nameTranslationKey;
    private String descriptionTranslationKey;

    public PowerType(Identifier id, PowerFactory<T>.Instance factory) {
        this.identifier = id;
        this.factory = factory;
    }

    public Identifier getIdentifier() {
        return identifier;
    }

    public PowerFactory<T>.Instance getFactory() {
        return factory;
    }

    public PowerType setHidden() {
        this.isHidden = true;
        return this;
    }

    public void setTranslationKeys(String name, String description) {
        this.nameTranslationKey = name;
        this.descriptionTranslationKey = description;
    }

    public T create(LivingEntity entity) {
        return getFactory().apply(this, entity);
    }

    public boolean isHidden() {
        return this.isHidden;
    }

    public boolean isActive(Entity entity) {
        if(entity instanceof LivingEntity && identifier != null) {
            PowerHolderComponent component = PowerHolderComponent.KEY.get(entity);
            if(component.hasPower(this)) {
                return component.getPower(this).isActive();
            }
        }
        return false;
    }

    public T get(Entity entity) {
        if(entity instanceof LivingEntity) {
            PowerHolderComponent component = PowerHolderComponent.KEY.get(entity);
            return component.getPower(this);
        }
        return null;
    }

    public String getOrCreateNameTranslationKey() {
        if(nameTranslationKey == null || nameTranslationKey.isEmpty()) {
            nameTranslationKey =
                "power." + identifier.getNamespace() + "." + identifier.getPath() + ".name";
        }
        return nameTranslationKey;
    }

    public MutableText getName() {
        return Text.translatable(getOrCreateNameTranslationKey());
    }

    public String getOrCreateDescriptionTranslationKey() {
        if(descriptionTranslationKey == null || descriptionTranslationKey.isEmpty()) {
            descriptionTranslationKey =
                "power." + identifier.getNamespace() + "." + identifier.getPath() + ".description";
        }
        return descriptionTranslationKey;
    }

    public MutableText getDescription() {
        return Text.translatable(getOrCreateDescriptionTranslationKey());
    }

    @Override
    public int hashCode() {
        return this.identifier.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == this) {
            return true;
        }
        if(!(obj instanceof PowerType)) {
            return false;
        }
        Identifier id = ((PowerType)obj).getIdentifier();
        return identifier.equals(id);
    }
}
