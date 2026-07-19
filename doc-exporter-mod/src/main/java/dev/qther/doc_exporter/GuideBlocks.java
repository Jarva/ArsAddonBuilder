package dev.qther.doc_exporter;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class GuideBlocks {
    public static final String NAMESPACE = "ars_guide";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NAMESPACE);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NAMESPACE);

    public static final DeferredBlock<Block> X_AXIS = registerBlock("x_axis");
    public static final DeferredBlock<Block> Y_AXIS = registerBlock("y_axis");
    public static final DeferredBlock<Block> Z_AXIS = registerBlock("z_axis");
    public static final DeferredBlock<Block> CORNER = registerBlock("corner");

    private GuideBlocks() {
    }

    private static DeferredBlock<Block> registerBlock(String name) {
        DeferredBlock<Block> block = BLOCKS.registerSimpleBlock(name, Block.Properties.ofFullCopy(Blocks.WHITE_CONCRETE));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    public static void register(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
    }
}
