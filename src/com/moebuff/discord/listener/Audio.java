package com.moebuff.discord.listener;

import com.moebuff.discord.Settings;
import com.moebuff.discord.utils.io.FF;
import com.moebuff.discord.utils.io.FileHandle;
import com.moebuff.discord.utils.reflect.ReflectionUtil;
import com.moebuff.discord.utils.Log;
import com.moebuff.discord.utils.OS;
import com.moebuff.discord.utils.URLUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;
import sx.blah.discord.util.audio.AudioPlayer;
import sx.blah.discord.util.audio.events.TrackFinishEvent;
import sx.blah.discord.util.audio.events.TrackQueueEvent;
import sx.blah.discord.util.audio.events.TrackStartEvent;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 音频指令
 *
 * @author muto
 */
public class Audio {
    // Stores the last channel that the join command was sent from
    private static final Map<IGuild, IChannel> LAST_CHANNEL = new HashMap<>();
    private static final Map<IGuild, IVoiceChannel> LAST_VOICE = new HashMap<>();

    @EventSubscriber
    public static void onTrackQueue(TrackQueueEvent event)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        String msg = String.format("Added **%s** to the playlist.", getTrackTitle(event));
        LAST_CHANNEL.get(guild).sendMessage(msg);
    }

    @EventSubscriber
    public static void onTrackStart(TrackStartEvent event)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        String msg = String.format("Now playing **%s**.", getTrackTitle(event));
        LAST_CHANNEL.get(guild).sendMessage(msg);
    }

    @EventSubscriber
    public static void onTrackFinish(TrackFinishEvent event)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = event.getPlayer().getGuild();
        IChannel channel = LAST_CHANNEL.get(guild);
        String msg = String.format("Finished playing **%s**.", getTrackTitle(event));
        channel.sendMessage(msg);

        if (!event.getNewTrack().isPresent()) {
            channel.sendMessage("The playlist is now empty.");
        }
    }

    /**
     * 用于处理额外的参数，这是一组指令集，通常需要再次判断以确认需要执行的逻辑。
     *
     * @param guild   当前所在工会
     * @param channel 当前所在频道
     * @param user    当前用户
     * @param args    命令行参数
     */
    static void handle(IGuild guild, IChannel channel, IUser user, String[] args)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        if (args.length == 0) {
            channel.sendMessage("The command requires some additional parameters.");
            channel.sendMessage("For details, refer to the help documentation.");
            return;
        }

        boolean prompt = false;//是否需要提示
        String[] params = args.length > 1 ?
                Arrays.copyOfRange(args, 1, args.length) :
                new String[0];
        switch (args[0]) {
            case "-j":
            case "join":
                Audio.join(guild, channel, user);
                break;
            case "-L":
            case "leave":
                Audio.leave(guild, channel);
                break;
            case "-u":
            case "url":
            case "queueUrl":
                Audio.queueUrl(channel, String.join(" ", params));
                break;
            case "-f":
            case "file":
            case "queueFile":
                Audio.queueFile(channel, String.join(" ", params));
                break;
            case "-q":
            case "queue":
                String address = String.join(" ", params);
                try {
                    queueUrl(channel, new URL(address));
                } catch (MalformedURLException e) {
                    queueFile(channel, address);
                }
                break;
            case "-pl":
            case "play":
                Audio.player(channel).setPaused(false);
                break;
            case "-p":
                if (params.length > 0) {
                    Audio.player(channel).setPaused(false);
                    break;
                }
                prompt = true;
            case "pause":
                Audio.player(channel).setPaused(true);

                // 下面这行代码本应放在上面，之所以这么写，是为了避免因报错导致运行中断
                if (prompt) {
                    channel.sendMessage("Just add a parameter can continue to play.");
                }
                break;
            case "-s":
            case "skip":
                Audio.player(channel).skip();
                break;
            case "-l":
            case "list":
                Audio.list(channel);
                break;
        }
    }

    // Audio player methods
    //---------------------------------------------------------------------------------------------

    /**
     * 加入用户所在的语音频道，若已加入则只更新当前的文字频道。
     *
     * @param guild   用户所在工会
     * @param channel 用户所在的文字频道
     * @param user    当前用户
     */
    public static void join(IGuild guild, IChannel channel, IUser user)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        LAST_CHANNEL.put(guild, channel);
        
        List<IVoiceChannel> voiceChannels = guild.getVoiceChannels();
        IVoiceChannel voice  = guild.getAFKChannel();//default
        boolean isInVoiceChannel = false;
        
        for(IVoiceChannel v : voiceChannels){
            Log.getLogger().info(v.getName());
            List<IUser> users = v.getConnectedUsers();
            if(users.contains(user)){
                Log.getLogger().info(user.getName() + " is in " + v.getName());
                isInVoiceChannel = true;
                voice = v;
            }
            /*
            for(IUser u : users){
                if (u.getStringID().equals(user.getStringID())) {
                   isInVoiceChannel = true;
                    voice = v;
                }
            }
            */
        }
        if (!isInVoiceChannel) {
            channel.sendMessage("You aren't in a voice channel!");
        } else {
            IUser our = channel.getClient().getOurUser();
            int userLimit = voice.getUserLimit();
            if (!voice.getModifiedPermissions(our).contains(Permissions.VOICE_CONNECT)) {
                channel.sendMessage("I can't join that voice channel!");
            } else if (userLimit > 0 && voice.getConnectedUsers().size() >= userLimit) {
                channel.sendMessage("That room is full!");
            } else if (voice.getConnectedUsers().contains(user)) {
                voice.join();
                LAST_VOICE.put(guild, voice);
                String msg = String.format("Connected to **%s**.", voice.getName());
                channel.sendMessage(msg);
            } else if (ReflectionUtil.getCallerClass(2) == Audio.class) {
                channel.sendMessage("Updated to current channel");
            }
        }
    }

    private static void leave(IGuild guild, IChannel channel)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        if (!LAST_VOICE.containsKey(guild)) {
            List<IVoiceChannel> cs = channel.getClient().getConnectedVoiceChannels();
            for (IVoiceChannel c : cs) {
                Log.getLogger().trace(c.getName());

                // 以避免被拉去援交无法自救
                if (c.getGuild() == guild) {
                    LAST_VOICE.put(guild, c);
                    leave(guild, channel);
                    channel.sendMessage("This operation may be delayed or not useful.");
                    return;
                }
            }

            channel.sendMessage("I didn't join any channels!");
            return;
        }

        IVoiceChannel voice = LAST_VOICE.get(guild);
        if (voice.isConnected()) {
            player(channel).clean();
            voice.leave();
            LAST_CHANNEL.remove(guild);
            LAST_VOICE.remove(guild);
            channel.sendMessage("Note: The playlist has been cleared.");
            String msg = String.format("I have left the **%s**.", voice.getName());
            channel.sendMessage(msg);
        }
    }

    /**
     * 发送一个url请求，并将得到的音频加入播放队列
     *
     * @param channel 当前所在频道
     * @param spec    url字符串
     */
    public static void queueUrl(IChannel channel, String spec)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        try {
            queueUrl(channel, new URL(spec));
        } catch (MalformedURLException e) {
            channel.sendMessage("That URL is invalid!");
        }
    }

    public static void queueUrl(IChannel channel, URL url)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        try {
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-Agent", Settings.URL_AGENT);
            conn.setRequestProperty("Accept", "*/*");

            String name = FilenameUtils.getName(URLUtils.decode(url::getFile));
            queue(channel, new BufferedInputStream(conn.getInputStream()), name, "url", url);
        } catch (IOException e) {
            channel.sendMessage("Connection failed: " + e.getMessage());
        }
    }

    /**
     * 将从本地音频目录获取的指定文件加入到播放队列
     *
     * @param channel 用户当前所在频道
     * @param path    用于指示音频文件的抽象路径名
     */
    public static void queueFile(IChannel channel, String path)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        FileHandle audio = FF.SONGS.child(path);
        if (!audio.exists()) {
            channel.sendMessage("That file doesn't exist!");
        } else if (!audio.canRead()) {
            channel.sendMessage("I don't have access to that file!");
        } else {
            queue(channel, audio.read(0), path, "file", audio);
        }
    }

    /**
     * 获取控制当前音频的播放器
     *
     * @param channel 用户所在频道
     * @return 音频播放器
     */
    public static AudioPlayer player(IChannel channel) {
        return AudioPlayer.getAudioPlayerForGuild(channel.getGuild());
    }

    private static void list(IChannel channel)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        AudioPlayer ap = player(channel);
        List<AudioPlayer.Track> list = ap.getPlaylist();
        if (list.size() == 0) {
            channel.sendMessage("No currently playing content.");
            return;
        }

        String status = ap.isPaused() ? "Paused" : "Playing";
        for (int i = 0; i < list.size(); i++) {
            AudioPlayer.Track track = list.get(i);
            channel.sendMessage(String.format("%s.%s [%s] %s",
                    i + 1,
                    track.getMetadata().get("title"),
                    DateFormatUtils.formatUTC(track.getCurrentTrackTime(),
                            OS.DEFAULT_TIME_PATTERN),
                    i == 0 ? status : "Wait"));
        }
    }

    // Utility methods
    //---------------------------------------------------------------------------------------------

    private static String getTrackTitle(TrackQueueEvent event) {
        return getTrackTitle(event.getTrack());
    }

    private static String getTrackTitle(TrackStartEvent event) {
        return getTrackTitle(event.getTrack());
    }

    private static String getTrackTitle(TrackFinishEvent event) {
        return getTrackTitle(event.getOldTrack());
    }

    private static String getTrackTitle(AudioPlayer.Track track) {
        Map<String, Object> metadata = track.getMetadata();
        return metadata.containsKey("title") ? metadata.get("title")
                + "" : "Unknown Track";
    }

    private static void queue(IChannel channel, InputStream stream, String title,
                              String key, Object value)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        IGuild guild = channel.getGuild();
        if (!LAST_CHANNEL.containsKey(guild)) {
            channel.sendMessage("First, execute the ***join*** command.");
            channel.sendMessage("Then run this command again.");
            channel.sendMessage("Don't forget the command prefix.");
            return;
        }

        try {
            AudioInputStream audio = AudioSystem.getAudioInputStream(stream);
            AudioPlayer.Track track = player(channel).queue(audio);

            Map<String, Object> metadata = track.getMetadata();
            metadata.put("title", title);
            if (key != null) {
                metadata.put(key, value);
            }
        } catch (IOException e) {
            channel.sendMessage("An IO exception occured: " + e.getMessage());
            Log.getLogger().debug(guild.getName(), e);
        } catch (UnsupportedAudioFileException e) {
            channel.sendMessage("That type of file is not supported!");
        }
    }

    /**
     * 将包含特定音频的输入流加到播放队列，支持标题自定义。
     *
     * @param channel 当前所在频道
     * @param stream  包含音频信息的输入流
     * @param title   用于显示的标题
     */
    public static void queue(IChannel channel, InputStream stream, String title)
            throws RateLimitException, DiscordException, MissingPermissionsException {
        queue(channel, stream, title, null, null);
    }
}
