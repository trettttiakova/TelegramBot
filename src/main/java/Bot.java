import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Bot extends TelegramLongPollingBot {
    private enum Command {
        ADD,
        DELETE,
        NONE
    }

    private Command currentCommand = Command.NONE;

    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new Bot());
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }

    private void sendMsg(Message message, String text) {
        SendMessage sendMessage = new SendMessage();
        sendMessage.enableMarkdown(true);
        sendMessage.setChatId(message.getChatId().toString()); // to which chat we are sending the message
        //sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }
    
    //сейчас возвращаемое значение не используется, если что, сделаю void
    private Date correctDateFormat(String date) throws ParseException {
        SimpleDateFormat format = new SimpleDateFormat("dd/MM/yyyy");
        format.setLenient(false);
        return format.parse(date);
    }
    
    private void add(Long chatId, String text) throws BotException {
        try {
            FileWriter writer = new FileWriter(new File("output.txt"), true);
            PrintWriter printWriter = new PrintWriter(writer);
            String[] tokens = text.trim().split(" ");
            if (tokens.length < 2) {
                throw new BotException("Invalid event format.\nFormat should be: DD.MM.YYYY your event\nTry again");
            }
            try {
                correctDateFormat(tokens[0].replace('.', '/'));
            } catch (ParseException e) {
                throw new BotException("Invalid date format.\nFormat should be: DD.MM.YYYY\nTry again");
            }
            printWriter.println(chatId + " " + text);
            printWriter.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            throw new BotException("Unable to enter event now.\nPlease try again later");
        }
    }
   
    /*
    private boolean add(Long chatId, String text) {
        try {
            FileWriter writer = new FileWriter(new File("output.txt"), true);
            PrintWriter printWriter = new PrintWriter(writer);
            String[] tokens = text.trim().split(" ");
            if (tokens.length < 2) {
                return false;
            }
            try {
//                 check(text);
                correctDateFormat(tokens[0].replace('.', '/'));
            } catch (Exception e) {
                return false;
            }
            printWriter.println(chatId + " " + text);
            printWriter.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    */

    /*
    private  void check(String text) throws Exception {
        String[] tokens = text.trim().split(" ");
        if (tokens.length == 0) throw new Exception("error");
        String date = tokens[0];
        date = date.trim();
        boolean dateGood = date.matches("[0-9][0-9].[0-9][0-9].[0-9][0-9][0-9][0-9]");
        if (!dateGood) throw new Exception("error");
        String day = date.substring(0, 2);
        String month = date.substring(3, 5);
        String year = date.substring(6, 10);
        if (Integer.parseInt(day) > 31 || Integer.parseInt(month) > 12 || Integer.parseInt(year) > 2100)
            throw new Exception("error");
        if (tokens.length < 2 || tokens[1].length() == 0) throw new Exception("enter event");
    }
    */
    
    private void show(Message message) throws BotException {
        //  it will show all the events
        StringBuilder stringBuilder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new FileReader(new File("output.txt")))) {
            String s = "";
            while (s != null) {
                if (s.length() >= 2) {
                    String[] strings = s.split(" ");
                    System.out.println(strings[0] + " " + strings[1]);
                    if (Long.parseLong(strings[0]) == message.getChatId()) {
                        stringBuilder.append(s.substring(strings[0].length())).append("\n");
                    }
                }
                s = reader.readLine();
            }
            sendMsg(message, stringBuilder.toString());
        } catch (IOException e) {
            e.printStackTrace();
            throw new BotException("Unable to show events now.\nPlease try again later");
        }
    }
    
    /*
    private void show(Message message) {
        //  it will show all the events
        StringBuilder stringBuilder = new StringBuilder();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(new File("output.txt")));
            String s = "";
            while (s != null) {
                if (s.length() >= 2) {
                    String[] strings = s.split(" ");
                    System.out.println(strings[0] + " " + strings[1]);
                    if (Long.parseLong(strings[0]) == message.getChatId()) {
                        stringBuilder.append(s.substring(strings[0].length())).append("\n");
                    }
                }

                s = br.readLine();
            }
            sendMsg(message, stringBuilder.toString());
        } catch (FileNotFoundException e) {
            try {
                if (br != null)
                    br.close();
            } catch (IOException e1) {
                e.printStackTrace();
                sendMsg(message, "error");
            }
        } catch (IOException e) {
            e.printStackTrace();
            sendMsg(message, "error");
        }
    }
    */
    
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        try {
            if (message != null && message.hasText()) {
                String messageText = message.getText();
                switch (messageText) {
                    case "/start":
                        sendMsg(message, "I'm the Calendar Bot! I can do stuff!\n" +
                                "Try one of my commands: \n/help\n/add\n/show\n/delete");
                        currentCommand = Command.NONE;
                        break;
                    case "/add":
                        currentCommand = Command.ADD;
                        sendMsg(message, "Send me the date and the event like this: DD.MM.YYYY your event");
                        break;
                    case "/help":
                        sendMsg(message, "I'm ready to help!");
                        currentCommand = Command.NONE;
                        break;
                    case "/show":
                        show(message);
                        currentCommand = Command.NONE;
                        break;
            /*
            case "/delete":
                currentCommand = Command.DELETE;
                show(message);
                sendMsg(message, "Please send me the number of the event you want to delete");
            */
                    case "/stop":
                        currentCommand = Command.NONE;
                        sendMsg(message, "Ready for the new command!");
                        break;
                    default:
                        if (currentCommand.equals(Command.NONE)) {
                            sendMsg(message, "I don't know what you mean :(\n" +
                                    "Try one of these: \n/help\n/add\n/show\n/delete");
                        } else {
                            switch (currentCommand) {
                                case ADD:
                                    add(message.getChatId(), messageText);
                                    sendMsg(message, "Added successfully!");
                                    currentCommand = Command.NONE;
                                    break;
                                case DELETE:
                                    // TODO: delete the event
                                    break;
                                default:
                                    break;
                            }
                        }
                        break;
                }
            }
        } catch (BotException e) {
            sendMsg(message, e.getMessage());
        }
    }

    /*
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message != null && message.hasText()) {
            String messageText = message.getText();
            if (messageText.equals("/start")) {
                sendMsg(message, "I'm the Calendar Bot! I can do stuff!\n" +
                        "Try one of my commands: \n/help\n/add\n/show\n/delete");
                currentCommand = Command.NONE;
            }
            else if (messageText.equals("/add")) {
                currentCommand = Command.ADD;
                sendMsg(message, "Send me the date and the event like this: DD.MM.YYYY your event");
            }
            else if (messageText.equals("/help")) {
                sendMsg(message, "I'm ready to help!");
                currentCommand = Command.NONE;
            }
            else if (messageText.equals("/show")) {
                show(message);
                currentCommand = Command.NONE;
            }
           
//             else if (messageText.equals("/delete")) {
//                 currentCommand = Command.DELETE;
//                 show(message);
//                 sendMsg(message, "Please send me the number of the event you want to delete");
//             }
            
            else if (messageText.equals("/stop")) {
                currentCommand = Command.NONE;
                sendMsg(message, "Ready for the new command!");
            }
            else {
                if (currentCommand.equals(Command.NONE)) {
                    sendMsg(message, "I don't know what you mean :(\n" +
                            "Try one of these: \n/help\n/add\n/show\n/delete");
                } else {
                    switch (currentCommand) {
                        case ADD:
                            if (add(message.getChatId(), messageText)) {
                                sendMsg(message, "Added successfully!");
                                currentCommand = Command.NONE;
                            }
                            else {
                                sendMsg(message, "Invalid date format.\nFormat should be: DD.MM.YYYY\nTry again");
                            }
                            break;
                        case DELETE:
                            // TODO: delete the event
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }
    */

    public String getBotUsername() {
        return "TheFriendliestDoveBot";
    }

    public String getBotToken() {
        return "1089800373:AAGTZYoC1GgpFVfeDUDkkAJ_yE6PtvT7Wvk";
    }
}
