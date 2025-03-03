package github.nighter.smartspawner.nms;

import org.bukkit.entity.EntityType;
import java.util.EnumMap;
import java.util.Map;

public class TextureWrapper {
    private static final Map<EntityType, String> TEXTURE_MAP = new EnumMap<>(EntityType.class);
    private static final Map<EntityType, String> VERSION_SPECIFIC_TEXTURES = new EnumMap<>(EntityType.class);

    public static void initializeCommonTextures() {
        TEXTURE_MAP.put(EntityType.ALLAY, "df5de940bfe499c59ee8dac9f9c3919e7535eff3a9acb16f4842bf290f4c679f");
        TEXTURE_MAP.put(EntityType.AXOLOTL, "21c3aa0d539208b47972bf8e72f0505cdcfb8d7796b2fcf85911ce94fd0193d0");
        TEXTURE_MAP.put(EntityType.BAT, "81c5cc1f40005a33124c60384a0f17a36a7b19ae90f1c32dcda17b5b56280a43");
        TEXTURE_MAP.put(EntityType.BEE, "76ded8355a5625893e8041877af65683c2fd0aa09c3d3c1c94f5f80a0f8c22cb");
        TEXTURE_MAP.put(EntityType.BLAZE, "737623f79f7eb4f3f80da65b652cc44b2148eea41f9ffe2e86a23bdf49ab77b1");
        TEXTURE_MAP.put(EntityType.CAMEL, "7eb6ad908b8d5155bc4d249271ef6084d455dd0e70a4002eb148f9e20b9deb2c");
        TEXTURE_MAP.put(EntityType.CAT, "e0eeaa869f53fa989198f5595520aec9395509aba993596a86654b3a0f6ca4a6");
        TEXTURE_MAP.put(EntityType.CAVE_SPIDER, "eccc4a32d45d74e8b14ef1ffd55cd5f381a06d4999081d52eaea12e13293e209");
        TEXTURE_MAP.put(EntityType.CHICKEN, "4e8f3ea46735ad0c106c549ccb6cd489c97a41e73d092497ee424becd9dfd29c");
        TEXTURE_MAP.put(EntityType.COD, "7892d7dd6aadf35f86da27fb63da4edda211df96d2829f691462a4fb1cab0");
        TEXTURE_MAP.put(EntityType.COW, "b667c0e107be79d7679bfe89bbc57c6bf198ecb529a3295fcfdfd2f24408dca3");
        TEXTURE_MAP.put(EntityType.DOLPHIN , "8e9688b950d880b55b7aa2cfcd76e5a0fa94aac6d16f78e833f7443ea29fed3");
        TEXTURE_MAP.put(EntityType.DONKEY, "63a976c047f412ebc5cb197131ebef30c004c0faf49d8dd4105fca1207edaff3");
        TEXTURE_MAP.put(EntityType.DROWNED, "c84df79c49104b198cdad6d99fd0d0bcf1531c92d4ab6269e40b7d3cbbb8e98c");
        TEXTURE_MAP.put(EntityType.ELDER_GUARDIAN, "8df4fec3aa34ceddb025a42c9fc435a8029b062598b325322ba3cb5e5f351c1f");
        TEXTURE_MAP.put(EntityType.ENDERMAN, "4f24767c8138b3dfec02f77bd151994d480d4e869664ce09a26b19289212162b");
        TEXTURE_MAP.put(EntityType.ENDERMITE, "5bc7b9d36fb92b6bf292be73d32c6c5b0ecc25b44323a541fae1f1e67e393a3e");
        TEXTURE_MAP.put(EntityType.EVOKER, "630ce775edb65db8c2741bdfae84f3c0d0285aba93afadc74900d55dfd9504a5");
        TEXTURE_MAP.put(EntityType.FOX, "d8954a42e69e0881ae6d24d4281459c144a0d5a968aed35d6d3d73a3c65d26a");
        TEXTURE_MAP.put(EntityType.FROG, "2ca4a8e494582c62aaa2c92474b16d69cd63baa3d3f50a4b631d6559ca0f33f5");
        TEXTURE_MAP.put(EntityType.GHAST, "64ab8a22e7687cc4c78f3b6ff5b1eb04917b51cd3cd7dbce36171160b3c77ced");
        TEXTURE_MAP.put(EntityType.GOAT, "457a0d538fa08a7affe312903468861720f9fa34e86d44b89dcec5639265f03");
        TEXTURE_MAP.put(EntityType.GLOW_SQUID, "4cb07d905888f8472252f9cfa39aa317babcad30af08cfe751adefa716b02036");
        TEXTURE_MAP.put(EntityType.GUARDIAN, "b8e725779c234c590cce854db5c10485ed8d8a33fa9b2bdc3424b68bb1380bed");
        TEXTURE_MAP.put(EntityType.HOGLIN, "7ad7b5aeb220c079e319cd70ac8800e80774a9313c22f38e77afb89999e6ec87");
        TEXTURE_MAP.put(EntityType.HORSE, "e335e31961713353a76401e00c3454b7ca885b7784d5288d3227222d9b48d393");
        TEXTURE_MAP.put(EntityType.HUSK, "d674c63c8db5f4ca628d69a3b1f8a36e29d8fd775e1a6bdb6cabb4be4db121");
        TEXTURE_MAP.put(EntityType.IRON_GOLEM, "4271913a3fc8f56bdf6b90a4b4ed6a05c562ce0076b5344d444fb2b040ae57d");
        TEXTURE_MAP.put(EntityType.LLAMA, "9f7d90b305aa64313c8d4404d8d652a96eba8a754b67f4347dcccdd5a6a63398");
        TEXTURE_MAP.put(EntityType.MAGMA_CUBE, "38957d5023c937c4c41aa2412d43410bda23cf79a9f6ab36b76fef2d7c429");
        TEXTURE_MAP.put(EntityType.MULE, "a0486a742e7dda0bae61ce2f55fa13527f1c3b334c57c034bb4cf132fb5f5f");
        TEXTURE_MAP.put(EntityType.OCELOT, "5657cd5c2989ff97570fec4ddcdc6926a68a3393250c1be1f0b114a1db1");
        TEXTURE_MAP.put(EntityType.PANDA, "d5c3d618a70cc062e2edfaed15173e2a32ab6d773cf6050452e1b97fc66fb388");
        TEXTURE_MAP.put(EntityType.PARROT, "5df4b3401a4d06ad66ac8b5c4d189618ae617f9c143071c8ac39a563cf4e4208");
        TEXTURE_MAP.put(EntityType.PHANTOM, "adfe51801761660ebf6dae70e9cad588b2ef5e6cb2b3194d028a40ac0eebcdf5");
        TEXTURE_MAP.put(EntityType.PIG, "9b1760e3778f8087046b86bec6a0a83a567625f30f0d6bce866d4bed95dba6c1");
        TEXTURE_MAP.put(EntityType.PILLAGER, "4aee6bb37cbfc92b0d86db5ada4790c64ff4468d68b84942fde04405e8ef5333");
        TEXTURE_MAP.put(EntityType.POLAR_BEAR, "3d3cd8548e7dceb5c2394d1b00da2c61ffc0dde46229b10509eb27a0dcb23bfb");
        TEXTURE_MAP.put(EntityType.PUFFERFISH, "292350c9f0993ed54db2c7113936325683ffc20104a9b622aa457d37e708d931");
        TEXTURE_MAP.put(EntityType.RABBIT, "cd1d2bbc3d77ab0d119c031ea74d8781c93285cf736abc422baec1eb1560e8ca");
        TEXTURE_MAP.put(EntityType.RAVAGER, "cd20bf52ec390a0799299184fc678bf84cf732bb1bd78fd1c4b441858f0235a8");
        TEXTURE_MAP.put(EntityType.SALMON, "d4d001589b86c22cf24f1618fe7efef12932aa9148b5e4fc6ff4a614b990ae12");
        TEXTURE_MAP.put(EntityType.SHEEP, "a723893df4cfb9c7240fc47b560ccf6ddeb19da9183d33083f2c71f46dad290a");
        TEXTURE_MAP.put(EntityType.SHULKER, "537a294f6b7b4ba437e5cb35fb20f46792e7ac0a490a66132a557124ec5f997a");
        TEXTURE_MAP.put(EntityType.SILVERFISH, "bfe13237e1109cab7264dc53b0aa697cf9a7c62c984a269fb0755a288912bbca");
        TEXTURE_MAP.put(EntityType.SKELETON_HORSE, "ac7d8a16d3f0f98b598df93f2c2d34e75171cd52dbf4a1211d7b84c019416a40");
        TEXTURE_MAP.put(EntityType.SLIME, "c7d29dbf3d98213ec2fb0ca25da74779e57bd0c1234268f828a3ec9869e15a9c");
        TEXTURE_MAP.put(EntityType.SNIFFER, "fe5a8341c478a134302981e6a7758ea4ecfd8d62a0df4067897e75502f9b25de");
        TEXTURE_MAP.put(EntityType.SPIDER, "e5871c22b81c12e67f5aebd9afe0958b81cada6305c07599a07b01ab126ba2c4");
        TEXTURE_MAP.put(EntityType.SQUID, "d8705624daa2956aa45956c81bab5f4fdb2c74a596051e24192039aea3a8b8");
        TEXTURE_MAP.put(EntityType.STRAY, "2c5097916bc0565d30601c0eebfeb287277a34e867b4ea43c63819d53e89ede7");
        TEXTURE_MAP.put(EntityType.STRIDER, "125851a86ee1c54c94fc5bed017823dfb3ba08eddbcab2a914ef45b596c1603");
        TEXTURE_MAP.put(EntityType.TADPOLE, "987035f5352334c2cba6ac4c65c2b9059739d6d0e839c1dd98d75d2e77957847");
        TEXTURE_MAP.put(EntityType.TRADER_LLAMA, "56307f42fc88ebc211e04ea2bb4d247b7428b711df9a4e0c6d1b921589e443a1");
        TEXTURE_MAP.put(EntityType.TROPICAL_FISH, "d6dd5e6addb56acbc694ea4ba5923b1b25688178feffa72290299e2505c97281");
        TEXTURE_MAP.put(EntityType.TURTLE, "0a4050e7aacc4539202658fdc339dd182d7e322f9fbcc4d5f99b5718a");
        TEXTURE_MAP.put(EntityType.VEX, "b663134d7306bb604175d2575d686714b04412fe501143611fcf3cc19bd70abe");
        TEXTURE_MAP.put(EntityType.VILLAGER, "a36e9841794a37eb99524925668b47a62b5cb72e096a9f8f95e106804ae13e1b");
        TEXTURE_MAP.put(EntityType.VINDICATOR, "4f6fb89d1c631bd7e79fe185ba1a6705425f5c31a5ff626521e395d4a6f7e2");
        TEXTURE_MAP.put(EntityType.WANDERING_TRADER, "ee011aac817259f2b48da3e5ef266094703866608b3d7d1754432bf249cd2234");
        TEXTURE_MAP.put(EntityType.WARDEN, "f8c211d66c803aac15ab86f79c7edfd6c3b2034d23355a92f6bd42e835260be0");
        TEXTURE_MAP.put(EntityType.WITCH, "8aa986a6e1c2d88ff198ab2c3259e8d2674cb83a6d206f883bad2c8ada819");
        TEXTURE_MAP.put(EntityType.WOLF, "8f0b221786f193c06dd19a7875a903635113f84523927bb69764237fe20703de");
        TEXTURE_MAP.put(EntityType.ZOGLIN, "c19b7b5e9ffd4e22b890ab778b4795b662faff2b4978bf815574e48b0e52b301");
        TEXTURE_MAP.put(EntityType.ZOMBIE_HORSE, "171ce469cba4426c811f69be5d958a09bfb9b1b2bb649d3577a0c2161ad2f524");
        TEXTURE_MAP.put(EntityType.ZOMBIE_VILLAGER, "37e838ccc26776a217c678386f6a65791fe8cdab8ce9ca4ac6b28397a4d81c22");
        TEXTURE_MAP.put(EntityType.ZOMBIFIED_PIGLIN, "e935842af769380f78e8b8a88d1ea6ca2807c1e5693c2cf797456620833e936f");
    }

    public static String getTexture(EntityType type) {
        if (VERSION_SPECIFIC_TEXTURES.containsKey(type)) {
            return VERSION_SPECIFIC_TEXTURES.get(type);
        }
        return TEXTURE_MAP.get(type);
    }

    public static void addVersionSpecificTexture(EntityType type, String texture) {
        VERSION_SPECIFIC_TEXTURES.put(type, texture);
    }

    public static boolean hasTexture(EntityType type) {
        return TEXTURE_MAP.containsKey(type) || VERSION_SPECIFIC_TEXTURES.containsKey(type);
    }
}