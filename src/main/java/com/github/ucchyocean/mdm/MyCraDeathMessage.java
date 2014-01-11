/*
 * @author     tsuttsu305, ucchy
 * @license    GPLv3
 * @copyright  Copyright ucchy 2013
 * このソースコードは、tsuttsu305氏のリポジトリからフォークさせていただきました。感謝。
 */
package com.github.ucchyocean.mdm;

import java.io.File;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * @author ucchy
 * My-Craサーバー用、デスメッセージカスタマイズプラグイン
 */
public class MyCraDeathMessage extends JavaPlugin implements Listener {

    private boolean loggingDeathMessage;
    private boolean suppressDeathMessage;
    private boolean prefixWorld;
    private YamlConfiguration defaultMessages;

    /**
     * プラグイン有効時に呼び出されるメソッド
     * @see org.bukkit.plugin.java.JavaPlugin#onEnable()
     */
    @Override
    public void onEnable(){
        loadFiles();
        getServer().getPluginManager().registerEvents(this, this);
    }

    /**
     * config.ymlが存在するかどうかチェックし、
     * 存在しないならデフォルトのconfig.ymlをコピーするメソッド
     */
    private void loadFiles() {

        // フォルダやファイルがない場合は、作成したりする
        File dir = new File(getDataFolder().getAbsolutePath());
        if ( !dir.exists() ) {
            dir.mkdirs();
        }

        File file = new File(getDataFolder(), "config.yml");
        if ( !file.exists() ) {
            Utility.copyFileFromJar(getFile(), file, "config.yml", false);
        }

        file = new File(getDataFolder(), "messages.yml");
        if ( !file.exists() ) {
            Utility.copyFileFromJar(getFile(), file, "messages.yml", false);
        }

        // 再読み込み処理
        reloadConfig();

        // 設定の取得
        FileConfiguration config = getConfig();

        loggingDeathMessage = config.getBoolean("loggingDeathMessage", true);
        suppressDeathMessage = config.getBoolean("suppressDeathMessage", false);
        prefixWorld = config.getBoolean("prefixWorld", true);

        // メッセージのデフォルトを、Jarの中から読み込む
        defaultMessages = Utility.loadYamlFromJar(getFile(), "messages.yml");
    }

    /**
     * デスメッセージを取得するメソッド
     * @param cause プレイヤー死亡理由
     * @return 理由に応じたメッセージ。
     */
    public String getMessage(String cause) {

        String defaultMessage = defaultMessages.getString(
                cause, "&e" + cause + "(%p_%k_%i_%o)");

        File file = new File(getDataFolder(), "messages.yml");
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return config.getString(cause, defaultMessage);
    }

    /**
     * プレイヤーがサーバーに参加したときに呼び出されるメソッド
     * @param event プレイヤー参加イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerJoin(PlayerJoinEvent event) {

        // メッセージを設定する
        // 初参加なら、初参加メッセージを流す
        String message;
        if ( event.getPlayer().hasPlayedBefore() )
            message = getMessage("server_join").replace("%p", event.getPlayer().getName());
        else
            message = getMessage("server_join_first").replace("%p", event.getPlayer().getName());

        event.setJoinMessage(Utility.replaceColorCode(message));

    }

    /**
     * プレイヤーがサーバーから退出したときに呼び出されるメソッド
     * @param event プレイヤー退出イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {

        // メッセージを設定する
        event.setQuitMessage(Utility.replaceColorCode(
                getMessage("server_quit").replace("%p", event.getPlayer().getName())));
    }

    /**
     * プレイヤーが死亡したときに呼び出されるメソッド
     * @param event プレイヤー死亡イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event){

        // プレイヤーとプレイヤーが最後に受けたダメージイベントを取得
        Player deader = event.getEntity();
        Player killer = deader.getKiller();
        final EntityDamageEvent damageEvent = deader.getLastDamageCause();

        // 死亡メッセージ
        String deathMessage = event.getDeathMessage();

        // ダメージイベントを受けずに死んだ 死因不明
        if (damageEvent == null) {
            deathMessage = getMessage("unknown"); // Unknown
        }
        // ダメージイベントあり 原因によってメッセージ変更
        else {
            // ダメージイベントがEntityDamageByEntityEvent(エンティティが原因のダメージイベント)かどうかチェック
            if (damageEvent instanceof EntityDamageByEntityEvent) {
                // EntityDamageByEventのgetDamagerメソッドから原因となったエンティティを取得
                Entity damager = ((EntityDamageByEntityEvent) damageEvent).getDamager();

                // プレイヤー間攻撃
                if (damager instanceof Player) {
                    deathMessage = getMessage("pvp");
                }
                // 飼われている狼
                else if (damager instanceof Wolf && ((Wolf) damager).isTamed()){
                    //  飼い主取得
                    String tamer = ((Wolf)damager).getOwner().getName();
                    deathMessage = getDeathMessageByMob("tamewolf", (Wolf)damager);
                    deathMessage = deathMessage.replace("%o", tamer);
                }
                // 打たれた矢
                else if (damager instanceof Arrow) {
                    Arrow arrow = (Arrow)damager;
                    LivingEntity shooter = arrow.getShooter();
                    if ( shooter instanceof Player ) {
                        deathMessage = getMessage("arrow");
                    } else if ( shooter instanceof Skeleton ) {
                        deathMessage = getDeathMessageByMob("skeleton", (Skeleton)shooter);
                    } else {
                        deathMessage = getMessage("dispenser");
                    }
                }
                // エンダーパールのテレポート時のダメージ
                else if (damager instanceof EnderPearl) {
                    deathMessage = getMessage("enderpearl");
                }
                // 投げたポーションや雪玉など
                else if (damager instanceof Projectile) {
                    // 投げたプレイヤー取得
                    LivingEntity shooter = ((Projectile)damager).getShooter();
                    if ( shooter instanceof Player ) {
                        if ( damager instanceof ThrownPotion ) {
                            deathMessage = getMessage("potion");
                        } else {
                            deathMessage = getMessage("throw");
                        }
                    } else if ( shooter instanceof Witch ) {
                        deathMessage = getDeathMessageByMob("witch", (Witch)shooter);
                    }
                }
                // そのほかのMOBは直接設定ファイルから取得
                else {
                    // 直接 getMessage メソッドを呼ぶ
                    if ( damager instanceof LivingEntity ) {
                        deathMessage = getDeathMessageByMob(
                                damager.getType().toString().toLowerCase(),
                                (LivingEntity)damager);
                    } else {
                        deathMessage = getMessage(
                                damager.getType().toString().toLowerCase());
                    }
                }
            }
            // エンティティ以外に倒されたメッセージは別に設定
            else {
                String suffix = (killer == null || damageEvent.getCause() == DamageCause.SUICIDE) ? "" : "_killer";
                deathMessage = getMessage(damageEvent.getCause().toString().toLowerCase() + suffix);
            }
        }

        // %p を、死亡した人の名前で置き換えする
        deathMessage = deathMessage.replace("%p", deader.getName());

        // %k を、killerで置き換える
        if ( deathMessage.contains("%k") ) {
            if ( killer != null )
                deathMessage = deathMessage.replace("%k", killer.getName());
            else
                deathMessage = deathMessage.replace("%k", "");
        }

        // %i を、killerが持ってたアイテムで置き換える
        if ( deathMessage.contains("%i") ) {
            ItemStack hand = killer.getItemInHand();
            String handItemName = hand.getType().toString();
            if ( hand.getType().equals(Material.AIR) ) {
                handItemName = "素手";
            }
            deathMessage = deathMessage.replace("%i", handItemName);
        }
        // カラーコードを置き換える
        deathMessage = Utility.replaceColorCode(deathMessage);

        if ( prefixWorld ) {
            // ワールド名を頭につける
            World world = deader.getWorld();
            deathMessage = "[" + world.getName() + "] " + deathMessage;
        }

        if ( loggingDeathMessage ) {
            // ロギング
            getLogger().info(ChatColor.stripColor(deathMessage));
        }

        if ( suppressDeathMessage ) {
            // メッセージを消去して、OPにだけ送信する
            event.setDeathMessage("");
            Player[] players = getServer().getOnlinePlayers();
            for ( Player player : players ) {
                if ( player.isOp() ) {
                    player.sendMessage(deathMessage);
                }
            }
        } else {
            // メッセージを再設定する
            event.setDeathMessage(deathMessage);
        }
    }

    /**
     * コマンドが実行されたときに呼び出されるメソッド
     * @param event
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        // NOTE: AdminCmd など、killコマンドの実行時に、ダメージ原因（DamageCause）を設定しない
        //       お行儀の悪い子が多いので、プレイヤーコマンドに介在して、
        //       DamageCause を設定する必要がある。

        // killコマンドではないなら、用は無いので、終了する。
        if ( !(event.getMessage().toLowerCase().startsWith("/kill")) ) {
            return;
        }

        // killコマンドを実行された場合は、DamageCause.SUICIDEを設定する
        Player player = event.getPlayer();
        player.setLastDamageCause(new EntityDamageEvent(player, DamageCause.SUICIDE, 100F));
    }

    /**
     * 指定したMOBに関連したデスメッセージを取得する
     * @param cause
     * @param le
     * @return
     */
    private String getDeathMessageByMob(String cause, LivingEntity le) {

        String deathMessage = getMessage(cause);
        
        String mobName = "";
        if ( le != null ) {
            mobName = le.getCustomName();
            if ( mobName == null ) {
                mobName = "";
            }
        }

        return deathMessage.replace("%n", mobName);
    }
}