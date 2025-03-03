package ml.northwestwind.forgeautofish;

import ml.northwestwind.forgeautofish.config.Config;
import ml.northwestwind.forgeautofish.handler.AutoFishHandler;
import ml.northwestwind.forgeautofish.keybind.KeyBinds;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLPaths;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod("forgeautofish")
public class AutoFish {
    public static final String MODID = "forgeautofish";
    public static final Logger LOGGER = LogManager.getLogger();

    public static int width;
    public static int height;

    String[] listAccMain;


    public AutoFish() {
        listAccMain = new String[]{"chunxingng"};

        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Config.CLIENT);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(KeyBinds::register);
        FMLJavaModLoadingContext.get().getModEventBus().addListener(this::onClientSetup);
        Config.loadConfig(FMLPaths.CONFIGDIR.get().resolve("forgeautofish-client.toml").toString());
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class, ()->new IExtensionPoint.DisplayTest(()->"ANY", (remote, isServer)-> true));


    }

    public static MutableComponent getTranslatableComponent(String key, Object... args) {
        return MutableComponent.create(new TranslatableContents(key, null, args));
    }

    public static MutableComponent getLiteralComponent(String str) {
        return MutableComponent.create(new PlainTextContents.LiteralContents(str));
    }

    @SubscribeEvent
    public void onClientSetup(final FMLClientSetupEvent e) {
        //Minecraft.getInstance().getWindow().setWindowed(640, 480);
        width = Minecraft.getInstance().getWindow().getWidth();
        height = Minecraft.getInstance().getWindow().getHeight();
        // mod auto fix only applies to username choigaming
        String username = Minecraft.getInstance().getUser().getName();
        for(String account : listAccMain) {
            if(account.contains(username)) {
                AutoFishHandler.isMainAcc = true;
            }
        }
        new Thread(() -> {
            WebSKClient.main(new String[]{});}).start();
    }

}
