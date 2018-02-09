package com.moebuff.discord.listener;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moebuff.discord.Settings;
import com.moebuff.discord.oppai.Koohii;
import com.moebuff.discord.utils.Log;
import com.moebuff.discord.service.OSUIDManager;
import com.moebuff.discord.utils.URLUtils;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.DiscordException;
import sx.blah.discord.util.EmbedBuilder;
import sx.blah.discord.util.MissingPermissionsException;
import sx.blah.discord.util.RateLimitException;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;

public class OSUHandler {

    private static String RANK_A_ICON = "https://cdn.discordapp.com/emojis/365509580593299466.png";
    private static String RANK_S_ICON = "https://cdn.discordapp.com/emojis/365509580731449354.png";
    private static String RANK_SS_ICON = "https://cdn.discordapp.com/emojis/365509580622659585.png";

    public static void handle(IGuild guild, IChannel channel, IUser user, IMessage message, String[] args){
        if (args.length == 0) {
            channel.sendMessage("The command requires some additional parameters.");
            channel.sendMessage("For details, refer to the help documentation.");
            return;
        }

        boolean prompt = false;//是否需要提示
        String[] params = args.length > 1 ?
                Arrays.copyOfRange(args, 1, args.length) :
                new String[0];
        String param_with_spacebar = "";

        if(args.length>1){
            for(int i = 1;i < args.length;i++){
                param_with_spacebar += args[i];
                param_with_spacebar += " ";
            }
        }
        Log.getLogger().info("****param_with_spacebar****:" + param_with_spacebar);
        switch (args[0]){
            case "setid":
                try {
                    setOSUId(channel, user, param_with_spacebar);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "recent":
                try {
                    OSURecent(channel, user, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "profile":
                try {
                    OSUProfile(channel, user, message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case "pp":
                pp(channel, args);
                break;
            default:
                channel.sendMessage("unknown command");
                break;
        }
    }

    private static void setOSUId(IChannel channel, IUser user, String id) throws IOException, RateLimitException, DiscordException, MissingPermissionsException {
        if (checkOSUId(channel, user, id)) {
            OSUIDManager.put(user.getStringID(), id);
            channel.sendMessage("osu!id set:    " + user.getName() + " : " + id);
        }
    }

    private static boolean checkOSUId(IChannel channel, IUser user, String id) throws IOException, RateLimitException, DiscordException, MissingPermissionsException {
        String username = OSUIDManager.get(user.getStringID());
        //command param is null or 0 length
        if ("".equals(id) || id == null) {
            channel.sendMessage("please add your id after %setid command. (e.g. %setid cookiezi)");
            return false;
        }

        //already set id
        if (username != null && !"".equals(username)) {
            channel.sendMessage("you have already set your id: " + username);
            return false;
        }

        //check if username exists (from osu!api)
        URLConnection conn = URLUtils.openConnection(new URL("https://osu.ppy.sh/api/get_user?"
                + "k=" + Settings.OSU_APIKEY
                + "&u=" + id.replaceAll(" ", "+"))
        );
        conn.setRequestProperty("accept", "*/*");
        conn.setRequestProperty("connection", "Keep-Alive");
        conn.setRequestProperty("contentType", "application/json");
        conn.setRequestProperty("User-agent", Settings.URL_AGENT);

        BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String result = "";
        String line;
        while ((line = bf.readLine()) != null) {
            result += line;
        }
        if (result.length() < 4) {
            channel.sendMessage("I can't find this ID in osu");
            return false;
        }
        bf.close();
        return true;
    }

    private static void OSURecent(IChannel channel, IUser user, IMessage message) throws RateLimitException, DiscordException, MissingPermissionsException, IOException {
        String username = OSUIDManager.get(user.getStringID());
        if ("".equals(username) || username == null) {
            message.reply("set your osu!id first please!(use " + Settings.PREFIX + "setid [id] command");
        } else {
            URLConnection conn = URLUtils.openConnection(new URL("https://osu.ppy.sh/api/get_user_recent?"
                            + "k=" + Settings.OSU_APIKEY
                            + "&u=" + username.replaceAll(" ", "+")
                            + "&limit=" + "1"
                    )
            );
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("contentType", "application/json");
            conn.setRequestProperty("User-agent", Settings.URL_AGENT);

            BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String result = "";
            String line = "";
            while ((line = bf.readLine()) != null) {
                result += line;
            }
            bf.close();
            if (result.length() < 4) {
                channel.sendMessage("no score found in last 24h.");
                return;
            }

            JsonParser parser = new JsonParser();
            JsonElement json = parser.parse(result);
            JsonArray jsonArray = json.getAsJsonArray();
            JsonElement ele = jsonArray.get(0);
            JsonObject obj = ele.getAsJsonObject();

            String userid = obj.get("user_id").getAsString();
            String beatmap_id = obj.get("beatmap_id").getAsString();
            String score = obj.get("score").getAsString();
            /*String maxcombo = obj.get("maxcombo").getAsString();
            String count50 =  obj.get("count50").getAsString();
            String count100 =  obj.get("count100").getAsString();
            String count300 =  obj.get("count300").getAsString();
            String countmiss =  obj.get("countmiss").getAsString();*/
            String date = obj.get("date").getAsString();
            String rank = obj.get("rank").getAsString();
            String userpage = "https://osu.ppy.sh/u/" + userid;
            String beatmapLink = "https://osu.ppy.sh/b/" + beatmap_id;
            String resultMsg = "```\n" +
                    "Username: " + username + "\n" +
                    "Beatmap: " + beatmapLink + "\n" +
                    "Userpage: " + userpage + "\n" +
                    "Score: " + score + "\n" +
                    "Date: " + date + "\n" +
                    "Rank: " + rank + "\n" +
                    "```";
            channel.sendMessage(resultMsg);
        }
    }

    private static void OSUProfile(IChannel channel, IUser user, IMessage message) throws RateLimitException, DiscordException, MissingPermissionsException, IOException {
        String username = OSUIDManager.get(user.getStringID());
        //Systemessage.out.println("username:" + username);
        if ("".equals(username) || username == null) {
            message.reply("set your osu!id first please!(use " + Settings.PREFIX + "setid [id] command");
        } else {
            URLConnection conn = URLUtils.openConnection(new URL("https://osu.ppy.sh/api/get_user?"
                            + "k=" + Settings.OSU_APIKEY
                            + "&u=" + username.replaceAll(" ", "+")
                    )
            );
            conn.setRequestProperty("accept", "*/*");
            conn.setRequestProperty("connection", "Keep-Alive");
            conn.setRequestProperty("contentType", "application/json");
            conn.setRequestProperty("User-agent", Settings.URL_AGENT);

            BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String result = "";
            String line = "";
            while ((line = bf.readLine()) != null) {
                result += line;
            }
            bf.close();
            if (result.length() < 4) {
                channel.sendMessage("no score found in last 24h.");
                return;
            }

            JsonParser parser = new JsonParser();
            JsonElement json = parser.parse(result);
            JsonArray jsonArray = json.getAsJsonArray();
            JsonElement ele = jsonArray.get(0);
            JsonObject obj = ele.getAsJsonObject();


            String userid = obj.get("user_id").getAsString();
            String _username = obj.get("username").getAsString();
            String playcount = obj.get("playcount").getAsString();
            String ranked_score = obj.get("ranked_score").getAsString();
            String total_score = obj.get("total_score").getAsString();
            String pp_rank = obj.get("pp_rank").getAsString();
            String level = obj.get("level").getAsString();
            String pp_raw = obj.get("pp_raw").getAsString();
            String accuracy = obj.get("accuracy").getAsString().substring(0,4);
            String count_rank_ss = obj.get("count_rank_ss").getAsString();
            String count_rank_s = obj.get("count_rank_s").getAsString();
            String count_rank_a = obj.get("count_rank_a").getAsString();
            String country = obj.get("country").getAsString();
            String pp_country_rank = obj.get("pp_country_rank").getAsString();
            String userpage = "https://osu.ppy.sh/u/" + userid;
            /* 改用EmbedObject
            String resultMsg = "```\n" +
                    "Username: " + _username + "\n" +
                    "Userpage: " + userpage + "\n" +
                    "Playcount: " + playcount + "\n" +
                    "PP: " + pp_raw + "\n" +
                    "Accuracy: " + accuracy + "\n" +
                    "Global Rank: " + pp_rank + "\n" +
                    "Country Rank: " + pp_country_rank + "\n" +
                    "Ranked Score: " + ranked_score + "\n" +
                    "Total Score: " + total_score + "\n" +
                    "Level: " + level + "\n" +
                    "```";
            */

            EmbedObject embedObject = new EmbedBuilder()
                    .withAuthorName(_username + "'s profile " + country)
                    .withAuthorIcon("https://a.ppy.sh/" + userid)
                    .withAuthorUrl(userpage)
                    .withColor(255,255,255)
                    .appendField("PlayCount: ", playcount, true)
                    .appendField("PP: ",pp_raw, true)
                    .appendField("Global Rank: ",pp_rank, false)
                    .appendField("Country Rank: ",pp_country_rank, true)
                    .appendField("Accuracy: ",accuracy,true)
                    .appendField("Ranked Score: ", ranked_score, false)
                    .appendField("Total Score: ",total_score,true)
                    .appendField("Level:", level, true)
                    .build();
            channel.sendMessage(embedObject);
        }
    }

    //暂时不考虑新主页的beatmap链接
    //如果是beatmapset链接(s/xxxxx)默认返回主难度
    //目前oppai的java库只支持std和taiko，而且taiko的转谱好像还不准
    public static void pp(IChannel channel, String[] args) {
        if (args.length == 0 || args.length > 1) {
            channel.sendMessage("please use this command like `%pp https://osu.ppy.sh/s/648232`");
            return;
        }
        String beatmapid = "";
        String params = "";

        String artist = "artist";
        String title = "";
        String mapper = "";
        String diffName = "";
        String star = "";
        //根据setid返回主难度id
        if (args[0].matches("(https://)?osu.ppy.sh/s/\\d+")) {
            String setid = args[0].substring(args[0].lastIndexOf("/") + 1);
            params = "k=" + Settings.OSU_APIKEY + "&s=" + setid;
            try {
                URL url = new URL("https://osu.ppy.sh/api/get_beatmaps");
                URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-agent", Settings.URL_AGENT);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                BufferedWriter bw = new BufferedWriter((new OutputStreamWriter(conn.getOutputStream())));
                bw.write(params);
                bw.flush();
                bw.close();
                BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String result = "";
                String line;
                while ((line = bf.readLine()) != null) {
                    result += line;
                }
                bf.close();
                if(result.length()<4){
                    channel.sendMessage("beatmap does not exist.");
                    return;
                }
                JsonObject jsonObject = new JsonParser().parse(result).getAsJsonArray().get(0).getAsJsonObject();
                beatmapid = jsonObject.get("beatmap_id").getAsString();
                artist = jsonObject.get("artist").getAsString();
                title = jsonObject.get("title").getAsString();
                mapper = jsonObject.get("creator").getAsString();
                diffName = jsonObject.get("version").getAsString();
                star = jsonObject.get("difficultyrating").getAsString();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else if (args[0].matches("(https://)?osu.ppy.sh/b/\\d+")) {
            beatmapid = args[0].substring(args[0].lastIndexOf("/") + 1);
            params = "k=" + Settings.OSU_APIKEY + "&b=" + beatmapid;
            try {
                URL url = new URL("https://osu.ppy.sh/api/get_beatmaps");
                URLConnection conn = url.openConnection();
                conn.setRequestProperty("User-agent", Settings.URL_AGENT);
                conn.setDoInput(true);
                conn.setDoOutput(true);
                BufferedWriter bw = new BufferedWriter((new OutputStreamWriter(conn.getOutputStream())));
                bw.write(params);
                bw.flush();
                bw.close();
                BufferedReader bf = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String result = "";
                String line;
                while ((line = bf.readLine()) != null) {
                    result += line;
                }
                bf.close();
                if(result.length()<4){
                    channel.sendMessage("beatmap does not exist.");
                    return;
                }
                JsonObject jsonObject = new JsonParser().parse(result)
                        .getAsJsonArray().get(0).getAsJsonObject();
                artist = jsonObject.get("artist").getAsString();
                title = jsonObject.get("title").getAsString();
                mapper = jsonObject.get("creator").getAsString();
                diffName = jsonObject.get("version").getAsString();
                star = jsonObject.get("difficultyrating").getAsString();
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        //获取难度文件(.osu)，oppai根据文件计算难度
        try {
            URL url = new URL("https://osu.ppy.sh/osu/" + beatmapid);
            URLConnection conn = url.openConnection();
            conn.setRequestProperty("User-agent", Settings.URL_AGENT);
            conn.setDoInput(true);
            ReadableByteChannel rbc = Channels.newChannel(url.openStream());
            File file = new File("diff.osu");
            FileOutputStream fos = new FileOutputStream(file);
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.flush();
            fos.close();

            //oppai by Koohii
            BufferedReader stdin = new BufferedReader(new InputStreamReader(new FileInputStream(file)));
            Koohii.Map beatmap = new Koohii.Parser().map(stdin);
            Koohii.DiffCalc stars = new Koohii.DiffCalc().calc(beatmap);
            Koohii.PPv2 pp = new Koohii.PPv2(
                    stars.aim, stars.speed, beatmap);
            channel.sendMessage(String.format("%s - %s[%s]\n" +
                            "Beatmap by %s\n" +
                            "Star Rating: %s\n" +
                            "pp for SS: %s",
                    artist,title,diffName,mapper,star,pp.total));
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}