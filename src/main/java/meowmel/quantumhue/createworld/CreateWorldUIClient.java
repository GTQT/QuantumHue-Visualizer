package meowmel.quantumhue.createworld;

import meowmel.quantumhue.createworld.api.tab.TabRegistry;
import meowmel.quantumhue.createworld.tab.GameTab;
import meowmel.quantumhue.createworld.tab.MoreTab;
import meowmel.quantumhue.createworld.tab.WorldTab;

/**
 * 创建世界界面的客户端初始化入口。
 * <p>Client-side init entry point for the create-world screen UI.</p>
 * 注册三个内置标签页：游戏(100)、世界(101)、更多(102)。
 */
public class CreateWorldUIClient {

    private CreateWorldUIClient() {
    }

    /**
     * 注册内置标签页到 CreateWorldUI 的 bar 下。
     * <p>Registers the built-in tabs to the create-world tab bar.</p>
     * 必须在 GUI 初始化之前调用（TabRegistry 冻结后不可再注册）。
     */
    public static void registerTabs() {
        TabRegistry.registerTab(GameTab::new, 100, "quantumhue.createworld.tab.game", 0);
        TabRegistry.registerTab(WorldTab::new, 101, "quantumhue.createworld.tab.world", 1);
        TabRegistry.registerTab(MoreTab::new, 102, "quantumhue.createworld.tab.more", 2);
    }
}
