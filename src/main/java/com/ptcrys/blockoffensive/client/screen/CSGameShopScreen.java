package com.ptcrys.blockoffensive.client.screen;

import com.ptcrys.blockoffensive.BlockOffensive;
import com.ptcrys.blockoffensive.client.data.CSClientData;
import com.ptcrys.blockoffensive.client.renderer.ShopSlotRenderer;
import com.ptcrys.blockoffensive.map.shop.ItemType;
import com.ptcrys.fpsmatch.common.client.FPSMClient;
import com.ptcrys.fpsmatch.common.client.music.FPSClientMusicManager;
import com.ptcrys.fpsmatch.common.client.shop.ClientShopSlot;
import com.ptcrys.fpsmatch.common.client.shop.ShopActionResultListener;
import com.ptcrys.fpsmatch.common.packet.FPSMSoundPlayC2SPacket;
import com.ptcrys.fpsmatch.common.packet.register.NetworkPacketRegister;
import com.ptcrys.fpsmatch.common.packet.shop.ShopActionC2SPacket;
import com.ptcrys.fpsmatch.common.packet.shop.ShopActionResultS2CPacket;
import com.ptcrys.fpsmatch.common.sound.FPSMSoundRegister;
import com.ptcrys.fpsmatch.compat.LrtacticalCompat;
import com.ptcrys.fpsmatch.compat.gun.GunCompatManager;
import com.ptcrys.fpsmatch.compat.impl.FPSMImpl;
import com.ptcrys.fpsmatch.core.shop.ShopAction;
import com.ptcrys.fpsmatch.core.shop.ShopActionResult;
import com.ptcrys.fpsmatch.util.FPSMUtil;
import com.ptcrys.fpsmatch.util.RenderUtil;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.GunDisplayInstance;
import icyllis.modernui.animation.TimeInterpolator;
import icyllis.modernui.animation.ValueAnimator;
import icyllis.modernui.core.Context;
import icyllis.modernui.fragment.Fragment;
import icyllis.modernui.graphics.Canvas;
import icyllis.modernui.graphics.Image;
import icyllis.modernui.graphics.drawable.ImageDrawable;
import icyllis.modernui.graphics.drawable.ShapeDrawable;
import icyllis.modernui.mc.MinecraftSurfaceView;
import icyllis.modernui.mc.ScreenCallback;
import icyllis.modernui.util.DataSet;
import icyllis.modernui.view.*;
import icyllis.modernui.widget.ImageView;
import icyllis.modernui.widget.LinearLayout;
import icyllis.modernui.widget.RelativeLayout;
import icyllis.modernui.widget.TextView;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static icyllis.modernui.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static icyllis.modernui.view.ViewGroup.LayoutParams.WRAP_CONTENT;

public class CSGameShopScreen extends Fragment implements ScreenCallback {
    public static final Map<ItemType, List<GunButtonLayout>> shopButtons = new HashMap<>();
    public static final String BACKGROUND = "ui/cs/background.png";
    public static final int DISABLE_TEXT_COLOR = RenderUtil.color(100, 100, 100);
    private static final String[] TOP_NAME_KEYS = new String[]{"blockoffensive.shop.title.equipment", "blockoffensive.shop.title.pistol", "blockoffensive.shop.title.mid_rank", "blockoffensive.shop.title.rifle", "blockoffensive.shop.title.throwable"};
    public static boolean refreshFlag = false;
    private static CSGameShopScreen INSTANCE;
    private static final AtomicLong NEXT_REQUEST_ID = new AtomicLong();
    private final Map<Long, PendingShopAction> pendingActions = new HashMap<>();
    private AutoCloseable resultSubscription;

    public CSGameShopScreen() {
    }

    public static float calculateScaleFactor(int w, int h) {
        return Math.min((float) w / 1920, (float) h / 1080);
    }

    public static CSGameShopScreen getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new CSGameShopScreen();
        }
        return INSTANCE;
    }

    public View onCreateView(@NotNull LayoutInflater inflater, ViewGroup container, DataSet savedInstanceState) {
        closeResultSubscription();
        resultSubscription = ShopActionResultListener.install(this::handleShopActionResult);
        return new WindowLayout(getContext());
    }

    @Override
    public void onDestroyView() {
        closeResultSubscription();
        pendingActions.clear();
        super.onDestroyView();
    }

    private void closeResultSubscription() {
        if (resultSubscription == null) return;
        try {
            resultSubscription.close();
        } catch (Exception ignored) {
        }
        resultSubscription = null;
    }

    private void sendShopAction(ItemType type, int index, ShopAction action) {
        if (pendingActions.values().stream().anyMatch(pending -> pending.matches(type, index, action))) return;
        long requestId = NEXT_REQUEST_ID.incrementAndGet();
        pendingActions.put(requestId, new PendingShopAction(type, index, action));
        NetworkPacketRegister.getChannelFromCache(ShopActionC2SPacket.class).sendToServer(
                new ShopActionC2SPacket(requestId, FPSMClient.getGlobalData().getCurrentMap(), type, index, action));
    }

    private void handleShopActionResult(ShopActionResultS2CPacket packet) {
        PendingShopAction pending = pendingActions.get(packet.requestId());
        if (pending == null || !pending.matches(packet.type(), packet.index(), packet.action())) return;
        pendingActions.remove(packet.requestId(), pending);
        if (packet.result().accepted()) {
            refreshFlag = true;
            if (packet.action() == ShopAction.BUY) playPurchaseSound(pending.type(), pending.index());
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null) {
            Component message = Component.translatable(shopResultKey(packet.result().code()));
            minecraft.player.displayClientMessage(message, true);
            minecraft.getNarrator().sayNow(message);
        }
    }

    private static String shopResultKey(ShopActionResult.Code code) {
        return "blockoffensive.shop.result." + code.name().toLowerCase(Locale.ROOT);
    }

    private static void playPurchaseSound(ItemType type, int index) {
        ItemStack itemStack = FPSMClient.getGlobalData().getSlotData(type.name(), index).itemStack();
        if (GunCompatManager.isGun(itemStack)) {
            FPSMUtil.getGunTypeByGunId(GunCompatManager.findProvider(itemStack).getGunId(itemStack))
                    .ifPresent(gunType -> FPSClientMusicManager.playSound(FPSMSoundRegister.getGunDropSound(gunType)));
            return;
        }
        SoundEvent sound = FPSMImpl.findLrtacticalMod() && LrtacticalCompat.isKnife(itemStack.getItem())
                ? FPSMSoundRegister.getKnifeDropSound()
                : FPSMSoundRegister.getItemDropSound(itemStack.getItem());
        FPSClientMusicManager.playSound(sound);
    }

    private record PendingShopAction(ItemType type, int index, ShopAction action) {
        private boolean matches(ItemType candidateType, int candidateIndex, ShopAction candidateAction) {
            return type == candidateType && index == candidateIndex && action == candidateAction;
        }

        private boolean matches(String candidateType, int candidateIndex, ShopAction candidateAction) {
            return type.name().equals(candidateType) && index == candidateIndex && action == candidateAction;
        }
    }

    public static class WindowLayout extends RelativeLayout {
        private float scale = 1.0f;
        private ImageView background;
        private RelativeLayout headBar;
        private LinearLayout content;
        private LinearLayout shopWindow;

        public TextView moneyText;
        public TextView cooldownText;
        public TextView nextRoundMinMoneyText;

        public List<TypeBarLayout> typeBarLayouts = new ArrayList<>();

        public WindowLayout(Context context) {
            super(context);
            init();
        }

        private void init() {
            // 设置布局参数
            setLayoutParams(new ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            background = new ImageView(getContext());
            ImageDrawable backgroundDrawable = new ImageDrawable(Image.create(BlockOffensive.MODID, BACKGROUND));
            backgroundDrawable.setAlpha(60);
            background.setImageDrawable(backgroundDrawable);
            background.setScaleType(ImageView.ScaleType.FIT_XY);
            addView(background);

            // 创建内容区域
            content = new LinearLayout(getContext());
            content.setOrientation(LinearLayout.HORIZONTAL);

            // 创建商店窗口
            shopWindow = new LinearLayout(getContext());
            shopWindow.setOrientation(LinearLayout.HORIZONTAL);

            // 添加类型栏
            for (int i = 0; i < 5; i++) {
                TypeBarLayout typeBar = new TypeBarLayout(getContext(), i);
                typeBarLayouts.add(typeBar);
                shopWindow.addView(typeBar);
            }

            content.addView(shopWindow);
            addView(content);

            // 创建头部栏
            headBar = new RelativeLayout(getContext());

            // 头部栏背景
            ImageView titleBarBackground = new ImageView(getContext());
            ImageDrawable titleBarBackgroundDrawable = new ImageDrawable(Image.create(BlockOffensive.MODID, BACKGROUND));
            titleBarBackgroundDrawable.setAlpha(60);
            titleBarBackground.setImageDrawable(titleBarBackgroundDrawable);
            titleBarBackground.setScaleType(ImageView.ScaleType.FIT_XY);
            headBar.addView(titleBarBackground, new RelativeLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));

            // 金钱文本
            moneyText = new TextView(getContext());
            moneyText.setTextColor(FPSMClient.getGlobalData().isCurrentTeam("ct") ? RenderUtil.color(150, 200, 250) : RenderUtil.color(234, 192, 85));
            moneyText.setTextSize(18);
            RelativeLayout.LayoutParams moneyParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            moneyParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            moneyParams.addRule(RelativeLayout.CENTER_VERTICAL);
            moneyParams.leftMargin = 25;
            headBar.addView(moneyText, moneyParams);

            // 冷却时间文本
            cooldownText = new TextView(getContext());
            cooldownText.setText(I18n.get("blockoffensive.shop.title.cooldown", CSClientData.shopCloseTime));
            cooldownText.setTextSize(18);
            RelativeLayout.LayoutParams cooldownParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            cooldownParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            headBar.addView(cooldownText, cooldownParams);

            // 下一轮最低金钱文本
            int nextRoundMoney = CSClientData.getNextRoundMinMoney();
            if(nextRoundMoney != -1){
                nextRoundMinMoneyText = new TextView(getContext());
                nextRoundMinMoneyText.setText(I18n.get("blockoffensive.shop.title.min.money", nextRoundMoney));
                nextRoundMinMoneyText.setTextSize(15);
                RelativeLayout.LayoutParams minMoneyParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
                minMoneyParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
                minMoneyParams.addRule(RelativeLayout.CENTER_VERTICAL);
                minMoneyParams.rightMargin = 20;
                headBar.addView(nextRoundMinMoneyText, minMoneyParams);
            }

            addView(headBar);

            // 初始更新文本
            updateText();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int width = MeasureSpec.getSize(widthMeasureSpec);
            int height = MeasureSpec.getSize(heightMeasureSpec);

            // 计算缩放因子
            scale = calculateScaleFactor(width, height);

            // 计算内容区域尺寸
            int contentWidth = (int) (950 * scale);
            int contentHeight = (int) (550 * scale);

            // 测量背景 - 与内容区域相同大小
            background.measure(
                    MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
            );

            // 测量内容区域
            content.measure(
                    MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
            );

            // 测量商店窗口
            shopWindow.measure(
                    MeasureSpec.makeMeasureSpec(contentWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
            );

            // 测量类型栏
            for (TypeBarLayout typeBar : typeBarLayouts) {
                int typeBarWidth = (int) ((TypeBarLayout.getGunButtonWeight(typeBar.i) + 30) * scale);
                typeBar.measure(
                        MeasureSpec.makeMeasureSpec(typeBarWidth, MeasureSpec.EXACTLY),
                        MeasureSpec.makeMeasureSpec(contentHeight, MeasureSpec.EXACTLY)
                );
                typeBar.setScale(scale);
            }

            // 测量头部栏
            int headBarWidth = (int) (950 * scale);
            int headBarHeight = (int) (38 * scale);
            headBar.measure(
                    MeasureSpec.makeMeasureSpec(headBarWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(headBarHeight, MeasureSpec.EXACTLY)
            );

            // 更新文本大小
            moneyText.setTextSize(18 * scale);
            cooldownText.setTextSize(18 * scale);
            if(nextRoundMinMoneyText != null){
                nextRoundMinMoneyText.setTextSize(15 * scale);

                RelativeLayout.LayoutParams minMoneyParams = (RelativeLayout.LayoutParams) nextRoundMinMoneyText.getLayoutParams();
                minMoneyParams.rightMargin = (int) (20 * scale);
                nextRoundMinMoneyText.setLayoutParams(minMoneyParams);
            }

            // 更新边距
            RelativeLayout.LayoutParams moneyParams = (RelativeLayout.LayoutParams) moneyText.getLayoutParams();
            moneyParams.leftMargin = (int) (25 * scale);
            moneyText.setLayoutParams(moneyParams);

            // 设置自身尺寸
            setMeasuredDimension(width, height);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            int width = right - left;

            // 计算内容区域位置 - 居中
            int contentWidth = content.getMeasuredWidth();
            int contentHeight = content.getMeasuredHeight();
            int contentLeft = (width - contentWidth) / 2;
            int contentTop = (int) (210 * scale);

            // 布局背景 - 与内容区域相同位置
            background.layout(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);

            // 布局内容区域
            content.layout(contentLeft, contentTop, contentLeft + contentWidth, contentTop + contentHeight);

            // 布局商店窗口
            shopWindow.layout(0, 0, shopWindow.getMeasuredWidth(), shopWindow.getMeasuredHeight());

            // 布局类型栏
            int typeBarLeft = 0;
            for (TypeBarLayout typeBar : typeBarLayouts) {
                int typeBarWidth = typeBar.getMeasuredWidth();
                typeBar.layout(typeBarLeft, 0, typeBarLeft + typeBarWidth, contentHeight);
                typeBarLeft += typeBarWidth;
            }

            // 布局头部栏 - 居中上方
            int headBarWidth = headBar.getMeasuredWidth();
            int headBarHeight = headBar.getMeasuredHeight();
            int headBarLeft = (width - headBarWidth) / 2;
            int headBarTop = (int) (170 * scale);
            headBar.layout(headBarLeft, headBarTop, headBarLeft + headBarWidth, headBarTop + headBarHeight);

            // 更新所有按钮的缩放
            for (List<GunButtonLayout> gunButtons : shopButtons.values()) {
                for (GunButtonLayout gunButton : gunButtons) {
                    gunButton.setScale(scale);
                }
            }
        }

        @Override
        public void draw(@NotNull Canvas canvas) {
            super.draw(canvas);
            updateText();
        }

        public void updateText() {
            int money = CSClientData.getMoney() == -1 ? 16000 : CSClientData.getMoney();
            moneyText.setText("$ " + money);
            moneyText.setTextColor(FPSMClient.getGlobalData().isCurrentTeam("ct") ? RenderUtil.color(150, 200, 250) : RenderUtil.color(234, 192, 85));
            if(nextRoundMinMoneyText != null){
                nextRoundMinMoneyText.setText(I18n.get("blockoffensive.shop.title.min.money", CSClientData.getNextRoundMinMoney()));
            }

            cooldownText.setText(I18n.get("blockoffensive.shop.title.cooldown", CSClientData.shopCloseTime));
        }
    }

    public static class TypeBarLayout extends LinearLayout {
        int i;
        LinearLayout titleBar;
        TextView numTab;
        TextView title;
        List<LinearLayout> guns = new ArrayList<>();
        List<LinearLayout> shops = new ArrayList<>();

        public TypeBarLayout(Context context, int i) {
            super(context);
            this.i = i;
            setOrientation(LinearLayout.VERTICAL);

            // 初始化标题栏
            titleBar = new LinearLayout(getContext());
            titleBar.setOrientation(LinearLayout.HORIZONTAL);

            int textColor = RenderUtil.color(203, 203, 203);

            // 数字标签
            numTab = new TextView(getContext());
            numTab.setTextColor(textColor);
            numTab.setText(String.valueOf(i + 1));
            numTab.setTextSize(15);
            numTab.setPadding(15, 10, 0, 0);
            numTab.setGravity(Gravity.LEFT);

            // 标题
            title = new TextView(getContext());
            title.setTextColor(textColor);
            title.setText(I18n.get(TOP_NAME_KEYS[i]));
            title.setTextSize(21);
            title.setGravity(Gravity.CENTER);

            // 添加数字标签和标题到标题栏
            titleBar.addView(numTab, new LinearLayout.LayoutParams(25, MATCH_PARENT));
            titleBar.addView(title, new LinearLayout.LayoutParams((getGunButtonWeight(i) - 25), MATCH_PARENT));

            // 添加标题栏到类型栏
            addView(titleBar, new LinearLayout.LayoutParams(MATCH_PARENT, 44));

            // 初始化商品按钮
            List<GunButtonLayout> buttons = new ArrayList<>();
            for (int j = 0; j < 5; j++) {
                LinearLayout shop = new LinearLayout(getContext());
                shop.setGravity(Gravity.CENTER);

                LinearLayout gun = new LinearLayout(getContext());
                GunButtonLayout gunButtonLayout = new GunButtonLayout(getContext(), ItemType.values()[i], j);
                buttons.add(gunButtonLayout);

                gun.addView(gunButtonLayout, new LinearLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT));
                guns.add(gun);

                shop.addView(gun, new LinearLayout.LayoutParams(getGunButtonWeight(i), 90));
                shops.add(shop);

                addView(shop, new LinearLayout.LayoutParams(MATCH_PARENT, 98));
            }

            // 添加按钮到全局管理
            shopButtons.put(ItemType.values()[i], buttons);
        }

        public static int getGunButtonWeight(int i) {
            return switch (i) {
                case 2 -> 180;
                case 3 -> 200;
                default -> 140;
            };
        }

        public void setScale(float scale) {
            // 更新数字标签
            numTab.setTextSize(15 * scale);
            numTab.setPadding((int) (15 * scale), (int) (10 * scale), 0, 0);

            // 更新标题
            title.setTextSize(21 * scale);

            // 更新标题栏布局参数
            LinearLayout.LayoutParams titleBarParams = (LinearLayout.LayoutParams) titleBar.getLayoutParams();
            titleBarParams.height = (int) (44 * scale);
            titleBar.setLayoutParams(titleBarParams);

            // 更新数字标签布局参数
            LinearLayout.LayoutParams numTabParams = (LinearLayout.LayoutParams) numTab.getLayoutParams();
            numTabParams.width = (int) (25 * scale);
            numTab.setLayoutParams(numTabParams);

            // 更新标题布局参数
            LinearLayout.LayoutParams titleParams = (LinearLayout.LayoutParams) title.getLayoutParams();
            titleParams.width = (int) ((getGunButtonWeight(i) - 25) * scale);
            title.setLayoutParams(titleParams);

            // 更新商品布局
            for (LinearLayout gun : guns) {
                LinearLayout.LayoutParams gunParams = (LinearLayout.LayoutParams) gun.getLayoutParams();
                gunParams.width = (int) (getGunButtonWeight(i) * scale);
                gunParams.height = (int) (90 * scale);
                gun.setLayoutParams(gunParams);
            }

            // 更新商店布局
            for (LinearLayout shop : shops) {
                LinearLayout.LayoutParams shopParams = (LinearLayout.LayoutParams) shop.getLayoutParams();
                shopParams.height = (int) (98 * scale);
                shop.setLayoutParams(shopParams);
            }

            // 更新自身布局参数
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            params.width = (int) ((getGunButtonWeight(i) + 30) * scale);
            setLayoutParams(params);
        }
    }

    public static class GunButtonLayout extends RelativeLayout {
        public final ItemType type;
        public final int index;
        public final ShapeDrawable background;
        public final RelativeLayout returnGoodsLayout;
        public final ValueAnimator backgroundAnimeFadeIn;
        public final ValueAnimator backgroundAnimeFadeOut;
        public final TextView numText;
        public final TextView itemNameText;
        public final TextView costText;
        public final TextView returnGoodsText;
        public MinecraftSurfaceView minecraftSurfaceView;
        public ShopSlotRenderer shopSlotRenderer;

        public GunButtonLayout(Context context, ItemType type, int index) {
            super(context);
            this.type = type;
            this.index = index;

            setGravity(Gravity.CENTER);

            // 设置背景
            this.background = new ShapeDrawable();
            background.setShape(ShapeDrawable.RECTANGLE);
            background.setColor(RenderUtil.color(42, 42, 42));
            background.setCornerRadius(3);
            background.setAlpha(210);
            setBackground(background);

            // 初始化Minecraft表面视图
            minecraftSurfaceView = new MinecraftSurfaceView(getContext());
            ClientShopSlot currentSlot = getSlot();
            Optional<GunDisplayInstance> display = ModList.get().isLoaded("tacz") ? TimelessAPI.getGunDisplay(currentSlot.itemStack()) : Optional.empty();

            RelativeLayout.LayoutParams msvParams;
            if (display.isPresent()) {
                msvParams = new RelativeLayout.LayoutParams(117, 59);
            } else {
                msvParams = new RelativeLayout.LayoutParams(39, 39);
            }
            msvParams.addRule(RelativeLayout.CENTER_IN_PARENT);
            minecraftSurfaceView.setLayoutParams(msvParams);

            this.shopSlotRenderer = new ShopSlotRenderer(this.type, this.index);
            minecraftSurfaceView.setRenderer(this.shopSlotRenderer);
            addView(minecraftSurfaceView);

            // 数字文本
            numText = new TextView(getContext());
            numText.setTextSize(13);
            numText.setText(String.valueOf(this.index + 1));
            RelativeLayout.LayoutParams numParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            numParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            numParams.leftMargin = 5;
            numParams.topMargin = 5;
            numText.setLayoutParams(numParams);
            addView(numText);

            // 物品名称文本
            itemNameText = new TextView(getContext());
            itemNameText.setTextSize(13);
            itemNameText.setText(this.getSlot().itemStack().isEmpty() ? I18n.get("blockoffensive.shop.slot.empty") : getSlot().name());
            RelativeLayout.LayoutParams itemNameParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_TOP);
            itemNameParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            itemNameParams.rightMargin = 5;
            itemNameParams.topMargin = 5;
            itemNameText.setLayoutParams(itemNameParams);
            addView(itemNameText);

            // 退货文本
            returnGoodsText = new TextView(getContext());
            returnGoodsText.setTextSize(15);
            returnGoodsText.setText("↩");
            RelativeLayout.LayoutParams returnGoodsParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            returnGoodsParams.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            returnGoodsParams.leftMargin = 5;
            returnGoodsParams.topMargin = 12;
            returnGoodsText.setLayoutParams(returnGoodsParams);

            returnGoodsLayout = new RelativeLayout(getContext()) {
                @Override
                public void setEnabled(boolean enabled) {
                    returnGoodsText.setAlpha(enabled ? 255 : 0);
                    super.setEnabled(enabled);
                }
            };
            returnGoodsLayout.addView(returnGoodsText);
            returnGoodsLayout.setOnClickListener((l) -> CSGameShopScreen.getInstance().sendShopAction(this.type, this.index, ShopAction.RETURN));
            returnGoodsLayout.setEnabled(false);
            addView(returnGoodsLayout);

            // 价格文本
            costText = new TextView(getContext());
            costText.setText("$ " + currentSlot.cost());
            costText.setTextSize(12);
            RelativeLayout.LayoutParams costParams = new RelativeLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
            costParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            costParams.rightMargin = 5;
            costParams.bottomMargin = 5;
            costText.setLayoutParams(costParams);
            addView(costText);

            // 背景动画
            backgroundAnimeFadeIn = ValueAnimator.ofInt(42, 72);
            backgroundAnimeFadeIn.setDuration(200);
            backgroundAnimeFadeIn.setInterpolator(TimeInterpolator.SINE);
            backgroundAnimeFadeIn.addUpdateListener(animation -> {
                int color = (int) animation.getAnimatedValue();
                this.background.setColor(RenderUtil.color(color, color, color));
            });

            backgroundAnimeFadeOut = ValueAnimator.ofInt(72, 42);
            backgroundAnimeFadeOut.setDuration(200);
            backgroundAnimeFadeOut.setInterpolator(TimeInterpolator.SINE);
            backgroundAnimeFadeOut.addUpdateListener(animation -> {
                int color = (int) animation.getAnimatedValue();
                this.background.setColor(RenderUtil.color(color, color, color));
            });

            // 点击事件
            setOnClickListener((v) -> {
                boolean enable = CSClientData.canOpenShop && CSClientData.getMoney() >= currentSlot.cost() && !currentSlot.itemStack().isEmpty() && !currentSlot.isLocked();
                if (enable){
                    CSGameShopScreen.getInstance().sendShopAction(this.type, this.index, ShopAction.BUY);
                }
            });
        }

        public void setStats(boolean enable) {
            background.setStroke(enable ? 1 : 0, RenderUtil.color(255, 255, 255));
            this.returnGoodsLayout.setEnabled(enable);
        }

        public void setElements(boolean enable) {
            ClientShopSlot currentSlot = getSlot();
            if (enable) {
                int color = FPSMClient.getGlobalData().isCurrentTeam("ct") ? RenderUtil.color(150, 200, 250) : RenderUtil.color(234, 192, 85);
                numText.setTextColor(color);
                itemNameText.setTextColor(color);
                costText.setTextColor(color);
            } else {
                numText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                itemNameText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
                costText.setTextColor(CSGameShopScreen.DISABLE_TEXT_COLOR);
            }

            if (currentSlot.boughtCount() > 0) {
                background.setStroke(1, RenderUtil.color(255, 255, 255));
            } else {
                background.setStroke(0, RenderUtil.color(255, 255, 255));
            }

            returnGoodsLayout.setEnabled(currentSlot.canReturn());
        }

        public ClientShopSlot getSlot() {
            return FPSMClient.getGlobalData().getSlotData(this.type.name(), this.index);
        }

        public void updateButtonState() {
            ClientShopSlot currentSlot = this.getSlot();
            boolean enable = CSClientData.canOpenShop && CSClientData.getMoney() >= currentSlot.cost() && !currentSlot.itemStack().isEmpty() && !currentSlot.isLocked();
            this.setElements(enable);

            if (!this.isHovered()) {
                backgroundAnimeFadeIn.start();
            } else {
                backgroundAnimeFadeOut.start();
            }

            if (refreshFlag) {
                ClientShopSlot data = getSlot();
                setStats(data.canReturn());
                ItemStack itemStack = data.itemStack();
                boolean empty = itemStack.isEmpty();
                this.itemNameText.setText(empty ? I18n.get("blockoffensive.shop.slot.empty") : data.name());
                this.costText.setText("$ " + data.cost());
                this.invalidate();

                if (this.type == ItemType.THROWABLE && this.index == 4) {
                    refreshFlag = false;
                }
            }
        }

        public void setScale(float scale) {
            ClientShopSlot currentSlot = getSlot();
            Optional<GunDisplayInstance> display = ModList.get().isLoaded("tacz") ? TimelessAPI.getGunDisplay(currentSlot.itemStack()) : Optional.empty();

            // 更新Minecraft表面视图尺寸
            RelativeLayout.LayoutParams msvParams = (RelativeLayout.LayoutParams) minecraftSurfaceView.getLayoutParams();
            if (display.isPresent()) {
                msvParams.width = (int) (117 * scale);
                msvParams.height = (int) (59 * scale);
            } else {
                msvParams.width = (int) (39 * scale);
                msvParams.height = (int) (39 * scale);
            }
            minecraftSurfaceView.setLayoutParams(msvParams);
            shopSlotRenderer.setScale(scale);

            // 更新文本尺寸
            numText.setTextSize(13 * scale);
            itemNameText.setTextSize(13 * scale);
            returnGoodsText.setTextSize(15 * scale);
            costText.setTextSize(12 * scale);

            // 更新边距
            RelativeLayout.LayoutParams numParams = (RelativeLayout.LayoutParams) numText.getLayoutParams();
            numParams.leftMargin = (int) (5 * scale);
            numParams.topMargin = (int) (5 * scale);
            numText.setLayoutParams(numParams);

            RelativeLayout.LayoutParams itemNameParams = (RelativeLayout.LayoutParams) itemNameText.getLayoutParams();
            itemNameParams.rightMargin = (int) (5 * scale);
            itemNameParams.topMargin = (int) (5 * scale);
            itemNameText.setLayoutParams(itemNameParams);

            RelativeLayout.LayoutParams returnGoodsParams = (RelativeLayout.LayoutParams) returnGoodsText.getLayoutParams();
            returnGoodsParams.leftMargin = (int) (5 * scale);
            returnGoodsParams.topMargin = (int) (12 * scale);
            returnGoodsText.setLayoutParams(returnGoodsParams);

            RelativeLayout.LayoutParams costParams = (RelativeLayout.LayoutParams) costText.getLayoutParams();
            costParams.rightMargin = (int) (5 * scale);
            costParams.bottomMargin = (int) (5 * scale);
            costText.setLayoutParams(costParams);

            // 更新自身布局参数
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) getLayoutParams();
            params.width = (int) (TypeBarLayout.getGunButtonWeight(this.type.ordinal()) * scale);
            params.height = (int) (90 * scale);
            setLayoutParams(params);
        }

        @Override
        public void draw(@NotNull Canvas canvas) {
            super.draw(canvas);
            updateButtonState();
        }
    }
}
