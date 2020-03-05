import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import java.io.*;

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
        sendMessage.setReplyToMessageId(message.getMessageId());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void add(Message message) {
        try {
            FileWriter writer = new FileWriter(new File("output.txt"), true);
            PrintWriter printWriter = new PrintWriter(writer);

            printWriter.println(message.getChatId() + " " + message.getText());
            printWriter.close();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        if (message != null && message.hasText()) {
            String messageText = message.getText();
            if (messageText.equals("/add")) {
                currentCommand = Command.ADD;
                sendMsg(message, "Send me the date and the event like this: DD.MM.YYYY your event");
            }
            else if (messageText.equals("/help")) {
                sendMsg(message, "I'm ready to help!");
            }
            else if (messageText.equals("/show")) {
                show(message);
            }
            else if (messageText.equals("/delete")) {
                currentCommand = Command.DELETE;
                show(message);
                sendMsg(message, "Please send me the number of the event you want to delete");
            }
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
                            // process the string in format "DD.MM.YYYY event"
                            add(update.getMessage());
                            sendMsg(message, "Added successfully!");
                            currentCommand = Command.NONE;
                            // or tell the format is invalid
                            break;
                        case DELETE:
                            // delete the event
                            sendMsg(message, "Deleted successfully!");
                            currentCommand = Command.NONE;
                            // or tell the format is invalid
                            break;
                        default:
                            break;
                    }
                }
            }
        }
    }

    public String getBotUsername() {
        return "TheFriendliestDoveBot";
    }

    public String getBotToken() {
        return "1089800373:AAGTZYoC1GgpFVfeDUDkkAJ_yE6PtvT7Wvk";
    }
}
