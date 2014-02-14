/*
 * @author     tsuttsu305, ucchy
 * @license    GPLv3
 * @copyright  Copyright tsuttsu305, ucchy 2013
 * このソースコードは、tsuttsu305氏のリポジトリからフォークさせていただきました。感謝。
 */
package com.github.ucchyocean.mdm;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
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
            copyFileFromJar(getFile(), file, "config.yml", false);
        }

        file = new File(getDataFolder(), "messages.yml");
        if ( !file.exists() ) {
            copyFileFromJar(getFile(), file, "messages.yml", false);
        }

        // 再読み込み処理
        reloadConfig();

        // 設定の取得
        FileConfiguration config = getConfig();

        loggingDeathMessage = config.getBoolean("loggingDeathMessage", true);
        suppressDeathMessage = config.getBoolean("suppressDeathMessage", false);
        prefixWorld = config.getBoolean("prefixWorld", true);

        // メッセージのデフォルトを、Jarの中から読み込む
        defaultMessages = loadYamlFromJar(getFile(), "messages.yml");
    }

    /**
     * デスメッセージを取得するメソッド
     * @param cause プレイヤー死亡理由
     * @return 理由に応じたメッセージ。
     */
    private String getMessage(String cause) {

        String defaultMessage = defaultMessages.getString(cause);
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
            message = getMessage("server_join");
        else
            message = getMessage("server_join_first");

        if ( message == null ) {
            return;
        }

        message = replaceColorCode(message.replace("%p", event.getPlayer().getName()));
        event.setJoinMessage(message);
    }

    /**
     * プレイヤーがサーバーから退出したときに呼び出されるメソッド
     * @param event プレイヤー退出イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerQuit(PlayerQuitEvent event) {

        // メッセージを設定する
        String message = getMessage("server_quit");

        if ( message == null ) {
            return;
        }

        message = replaceColorCode(message.replace("%p", event.getPlayer().getName()));
        event.setQuitMessage(message);
    }

    /**
     * プレイヤーが死亡したときに呼び出されるメソッド
     * @param event プレイヤー死亡イベント
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event){

        String ownerName = "";
        String customName = "";
        
        // プレイヤーとプレイヤーが最後に受けたダメージイベントを取得
        Player deader = event.getEntity();
        Player killer = deader.getKiller();
        final EntityDamageEvent damageEvent = deader.getLastDamageCause();

        String cause = null;

        // ダメージイベントを受けずに死んだ 死因不明
        if (damageEvent == null) {
            cause = "unknown"; // Unknown
        }
        // ダメージイベントあり 原因によってメッセージ変更
        else {
            // ダメージイベントがEntityDamageByEntityEvent(エンティティが原因のダメージイベント)かどうかチェック
            if (damageEvent instanceof EntityDamageByEntityEvent) {
                // EntityDamageByEventのgetDamagerメソッドから原因となったエンティティを取得
                Entity damager = ((EntityDamageByEntityEvent) damageEvent).getDamager();

                // プレイヤー間攻撃
                if (damager instanceof Player) {
                    cause = "pvp";
                }
                // 飼われている狼
                else if (damager instanceof Wolf && ((Wolf) damager).isTamed()){
                    //  飼い主取得
                    ownerName = ((Wolf)damager).getOwner().getName();
                    customName = getCustomName((Wolf)damager);
                    cause = "tamewolf";
                }
                // エンダーパールのテレポート時のダメージ
                else if (damager instanceof EnderPearl) {
                    cause = "enderpearl";
                }
                // そのほかのMOBは直接設定ファイルから取得
                else {
                    // 直接 getMessage メソッドを呼ぶ
                    cause = damager.getType().toString().toLowerCase();
                    if ( damager instanceof LivingEntity ) {
                        customName = getCustomName((LivingEntity)damager);
                    }
                }
            }
            // エンティティ以外に倒されたメッセージは別に設定
            else {
                String suffix;
                if (killer == null || damageEvent.getCause() == DamageCause.SUICIDE) {
                    suffix = "";
                } else {
                    suffix = "_killer";
                }
                cause = damageEvent.getCause().toString().toLowerCase() + suffix;
            }
        }

        // メッセージリソースが取得できなかった場合は何もしない
        String deathMessage = getMessage(cause);
        if ( deathMessage == null ) {
            
            if ( loggingDeathMessage ) {
                // ロギング
                getLogger().info(ChatColor.stripColor(event.getDeathMessage()));
                getLogger().warning("死因メッセージリソース " + cause + " が見つかりません。");
            }
            
            return;
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
            if ( killer != null ) {
                ItemStack hand = killer.getItemInHand();
                String handItemName = hand.getType().toString();
                if ( hand.getType().equals(Material.AIR) ) {
                    handItemName = "素手";
                }
                deathMessage = deathMessage.replace("%i", handItemName);
            } else {
                deathMessage = deathMessage.replace("%i", "");
            }
        }
        
        // %o を、狼のオーナー名で置き換えする
        if ( deathMessage.contains("%o") ) {
            deathMessage = deathMessage.replace("%o", ownerName);
        }
        
        // %n を、ネームタグ設定名で置き換えする
        if ( deathMessage.contains("%n") ) {
            deathMessage = deathMessage.replace("%n", customName);
        }
        
        // カラーコードを置き換える
        deathMessage = replaceColorCode(deathMessage);
        
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
     * 指定したMOBのネームタグ設定名を取得する
     * @param le
     * @return
     */
    private String getCustomName(LivingEntity le) {
        
        if ( le == null ) {
            return "";
        }
        
        String name = le.getCustomName();
        if ( name == null ) {
            return "";
        }
        
        return name;
    }

    /**
     * jarファイルの中に格納されているファイルを、jarファイルの外にコピーするメソッド
     * @param jarFile jarファイル
     * @param targetFile コピー先
     * @param sourceFilePath コピー元
     * @param isBinary バイナリファイルかどうか
     */
    private void copyFileFromJar(
            File jarFile, File targetFile, String sourceFilePath, boolean isBinary) {

        InputStream is = null;
        FileOutputStream fos = null;
        BufferedReader reader = null;
        BufferedWriter writer = null;

        File parent = targetFile.getParentFile();
        if ( !parent.exists() ) {
            parent.mkdirs();
        }

        try {
            JarFile jar = new JarFile(jarFile);
            ZipEntry zipEntry = jar.getEntry(sourceFilePath);
            is = jar.getInputStream(zipEntry);

            fos = new FileOutputStream(targetFile);

            if ( isBinary ) {
                byte[] buf = new byte[8192];
                int len;
                while ( (len = is.read(buf)) != -1 ) {
                    fos.write(buf, 0, len);
                }
                fos.flush();
                fos.close();
                is.close();

            } else {
                reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
                writer = new BufferedWriter(new OutputStreamWriter(fos));

                String line;
                while ((line = reader.readLine()) != null) {
                    writer.write(line);
                    writer.newLine();
                }

            }

        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if ( writer != null ) {
                try {
                    writer.flush();
                    writer.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( reader != null ) {
                try {
                    reader.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( fos != null ) {
                try {
                    fos.flush();
                    fos.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( is != null ) {
                try {
                    is.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }
    }

    /**
     * Jarファイル内から指定のyamlを直接読み込み、YamlConfigurationにして返すメソッド<br>
     * 読み込みもとのファイルは、UTF-8 で保存する。
     * @return
     */
    private YamlConfiguration loadYamlFromJar(File jarFile, String fileName) {

        YamlConfiguration messages = new YamlConfiguration();
        JarFile file = null;
        InputStream inputStream = null;
        try {
            file = new JarFile(jarFile);
            ZipEntry zipEntry = file.getEntry(fileName);
            inputStream = file.getInputStream(zipEntry);
            BufferedReader reader =
                    new BufferedReader(new InputStreamReader(inputStream, "UTF-8"));
            String line;
            while ( (line = reader.readLine()) != null ) {
                if ( line.contains(":") && !line.startsWith("#") ) {
                    String key = line.substring(0, line.indexOf(":")).trim();
                    String value = line.substring(line.indexOf(":") + 1).trim();
                    if ( value.startsWith("'") && value.endsWith("'") )
                        value = value.substring(1, value.length()-1);
                    messages.set(key, value);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if ( file != null ) {
                try {
                    file.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
            if ( inputStream != null ) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }

        return messages;
    }

    /**
     * 文字列内のカラーコードを置き換えする
     * @param source 置き換え元の文字列
     * @return 置き換え後の文字列
     */
    private String replaceColorCode(String source) {
        
        if ( source == null ) {
            return null;
        }
        return ChatColor.translateAlternateColorCodes('&', source);
    }
}