package com.moebuff.discord.listener;

import com.moebuff.discord.entity.ChessRoom;
import com.moebuff.discord.service.ChessLogManager;
import com.moebuff.discord.service.ChessRoomManager;
import com.moebuff.discord.utils.Log;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class FiveChess {

    private static BufferedImage image;
    private static File file;
    private static FileOutputStream fileOutputStream;

    private static int grid_count = 20;
    private static int grid_long = 20;
    private static int grid_size = grid_long*grid_long;
    private static int pieces_size = 14;

    private static Map<Integer, int[][]> roomMap = new HashMap<>();
    /**
     *  0 = 没下
     *  1 = 红
     *  -1 = 黑
     */
    private static int[][] chessboard = new int[grid_count+1][grid_count+1];

    static {
        try {
            image = ImageIO.read(new File("res/chessboards/empty.png"));
            //file = new File("res/chessboards/result.png");
            //fileOutputStream = new FileOutputStream(file);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for(int i = 0;i < chessboard[i].length;i++){
            Arrays.fill(chessboard[i],0);
            if(i == chessboard.length-1)
                break;
        }
    }

    public static void handle(IGuild guild, IChannel channel, IUser user, IMessage message, String[] args) throws IOException {
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
        switch (args[0]){
            case "start":
                start(channel, user);
                break;
            case "join":
                join(channel, user, params);
                break;
            case "place":
                place(channel, user, params);
                break;
            default:
                channel.sendMessage("unknown command.");
                break;
        }
    }

    private static void start(IChannel channel, IUser player1) throws IOException {
        //是否已经在房间里
        ChessRoom room = ChessRoomManager.createRoom(player1);
        Log.getLogger().info(room.toString());
        if(room == null){
            //返回null居然代表已经在房间里，太弱智了
            channel.sendMessage("you are already in a room");
            //读取文件
            File boardTXT = new File("res/chessboards/" + player1.getStringID() + ".txt");
            int[][] board = new int[grid_count+1][grid_count+1];
            FileReader reader = new FileReader(boardTXT);
            char[] c = new char[1024];
            String str = new String(c);
            String[] rows = str.split("\n");
            for(int i=0;i<rows.length-1;i++){
                String[] columns = rows[i].split(" ");
                for(int j=0;j<columns.length;j++){
                    board[i][j] = Integer.valueOf(columns[j]);
                }
            }
            roomMap.put(room.getId(),board);
            return;
        }

        //复制一份空棋盘
        File boardImage = new File("res/chessboards/" + room.getChessboard() + ".png");
        if(!boardImage.exists()){
            FileInputStream inputStream = new FileInputStream(new File("res/chessboards/empty.png"));
            FileOutputStream outputStream = new FileOutputStream(boardImage);
            byte[] b = new byte[1024];
            int n=0;
            while((n=inputStream.read(b))!=-1){
                outputStream.write(b, 0, n);
            }
            inputStream.close();
            outputStream.close();
        }

        //创建文件，初始化00000000000000000000000
        File boardTXT = new File("res/chessboards/" + player1.getStringID() + ".txt");
        if(!boardTXT.exists()){
            //其实不用判断
            int[][] board = new int[grid_count+1][grid_count+1];
            for(int i=0;i<board.length;i++){
                Arrays.fill(board[i], 0);
            }
            FileOutputStream outputStream = new FileOutputStream(boardImage);
            for(int i=0;i<board.length;i++){
                for(int j=0;j<board[i].length;j++){
                    outputStream.write(board[i][j]+48);//ASCII
                    outputStream.write(32);
                }
                outputStream.write(10);
            }
            outputStream.flush();
            outputStream.close();
            roomMap.put(room.getId(), board);
            channel.sendMessage(player1.getName() + " created room " + room.getId());
        }
    }


    private static void join(IChannel channel, IUser player2, String[] params){
        try {
            int roomid = Integer.valueOf(params[0]);
            int result = ChessRoomManager.joinRoom(player2, Integer.valueOf(roomid));
            switch (result){
                case -1:
                    channel.sendMessage("you are already in a room");
                    break;
                case 0:
                    channel.sendMessage("you are already in this room");
                    break;
                case 1:
                    channel.sendMessage(player2.getName() + " joined room" + params[0]);
                    break;
            }
        } catch (Exception e){
            channel.sendMessage("something is wrong");
        }
    }

    private static void place(IChannel channel, IUser player, String[] params){
        ChessRoom room = ChessRoomManager.getRoomByUser(player);
        if(room == null){
            channel.sendMessage("you are not in a room");
            return;
        }
        if(room.getPlayer1() == null || room.getPlayer2() == null){
            channel.sendMessage("game has not started yet");
            return;
        }
        try {
            int x = Integer.valueOf(params[0]);
            int y = Integer.valueOf(params[1]);
            int[][] board = roomMap.get(room.getId());
            if(x >= board.length || y > board[0].length){
                channel.sendMessage("index out of range");
                return;
            }
            if(board[x][y] != 0){
                channel.sendMessage("you can't place here");
                return;
            }
            int color = ChessRoomManager.getColor(player);
            board[x][y] = color;

            ChessLogManager.addLog(color,x,y,room);
            roomMap.put(room.getId(), board);

            File boardImage = new File("res/chessboards/" + room.getChessboard() + ".png");
            place(boardImage,x,y,color);
            channel.sendFile(boardImage);

            int win = win(board);
            if(win == 1){
                channel.sendMessage("red won");
            }else if(win == -1){
                channel.sendMessage("black won");
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /*private static void init() throws IOException {
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.setColor(Color.BLACK);
        int height = image.getHeight();
        int width = image.getWidth();
        for(int i = 0; i < height; i += grid_long){
            for(int j = 0; j < width; j += grid_long){
                graphics2D.drawLine(0 ,i ,width,i);
                graphics2D.drawLine(j,0,j,height);
            }
        }
        ImageIO.write(image, "png", file);
    }*/

    private static void place(File file, int x, int y, int color) throws IOException {
        BufferedImage image = ImageIO.read(file);
        Graphics2D graphics2D = image.createGraphics();
        graphics2D.setColor(color == 1?Color.RED:Color.BLACK);
        graphics2D.fillOval(grid_long*x-pieces_size/2,grid_long*y-pieces_size/2,pieces_size,pieces_size);
        update(image, file);
        //chessboard[x][y] = color;
    }

    private static void update(BufferedImage image, File file) throws IOException {
        ImageIO.write(image, "png", file);
    }

    private static int win(int [][] chessboard){
        int current = 0;
        int left = 0;
        int right = 0;
        int top = 0;
        int bottom = 0;
        int l_t = 0;
        int l_b = 0;
        int r_t = 0;
        int r_b = 0;
        for(int i = 0;i < chessboard.length;i++){
            for(int j = 0;j < chessboard[i].length;j++) {
                left = 0;
                right = 0;
                top = 0;
                bottom = 0;
                l_t = 0;
                l_b = 0;
                r_t = 0;
                r_b = 0;
                current = chessboard[i][j];
                int left_index = j - 1;
                while (left_index >= 0 && chessboard[i][left_index] == chessboard[i][j]) {
                    left++;
                    left_index--;
                }
                int right_index = j + 1;
                while (right_index < chessboard[i].length && chessboard[i][right_index] == chessboard[i][j]) {
                    right++;
                    right_index++;
                }
                int top_index = i - 1;
                while (top_index >= 0 && chessboard[top_index][j] == chessboard[i][j]){
                    top++;
                    top_index--;
                }
                int bottom_index = j + 1;
                while(bottom_index < chessboard.length && chessboard[bottom_index][j] == chessboard[i][j]) {
                    bottom++;
                    bottom_index++;
                }
                left_index = j - 1;
                top_index = i - 1;
                while(left_index >= 0 && top_index >= 0 && chessboard[left_index][top_index] == chessboard[i][j]) {
                    l_t++;
                    left_index--;
                    top_index--;
                }
                left_index = j - 1;
                bottom_index = i + 1;
                while(left_index >= 0 && bottom_index < chessboard.length && chessboard[bottom_index][left_index] == chessboard[i][j]) {
                    l_b++;
                    left_index--;
                    bottom_index++;
                }
                right_index = j + 1;
                top_index = i - 1;
                while(right_index < chessboard[i].length && top_index >= 0 && chessboard[top_index][right_index] == chessboard[i][j]) {
                    r_t++;
                    top_index--;
                    right_index++;
                }
                right_index = j + 1;
                bottom_index = i + 1;
                while(right_index < chessboard[i].length && bottom_index < chessboard.length && chessboard[bottom_index][right_index] == chessboard[i][j]) {
                    r_b++;
                    right_index++;
                    bottom_index++;
                }
                if((current == 1 || current == -1) && (left + right == 4 || top + bottom == 4 || l_t + r_b ==4 || l_b + r_t == 4)){
                    System.out.println("win");
                    return current;
                }
            }
        }
        return current;
    }
    /*
    public static void main(String[] args){
        try {
            //init();
            System.out.println(win());
            place(file,0,0,1);
            place(file,0,1,1);
            place(file,0,2,1);
            place(file,0,3,1);
            place(file,0,4,-1);
            System.out.println(win());
            place(file,4,8,-1);
            place(file,5,9,-1);
            place(file,6,10,-1);
            place(file,7,11,-1);
            place(file,8,12,-1);
            System.out.println(win());
        } catch (Exception e){
            e.printStackTrace();
        }
    }
    */
}
