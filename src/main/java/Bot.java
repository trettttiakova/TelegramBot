import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.sql.*;

public class Bot extends TelegramLongPollingBot {
    private final String userName = "root";
    private static final String passWord = "root";
    private final String connectionURL = "jdbc:mysql://localhost:3306/MyDB?useUnicode=true&characterSetResults=UTF-8&characterEncoding=UTF-8&useTimezone=true&serverTimezone=Europe/Moscow";
    private final String botName = "@YourCalendarBot";

    private enum Command {
        ADD,
        DELETE,
        NONE
    }

    private Command currentCommand = Command.NONE;

    static class Event {
        Date date;
        String text;
        long id;

        Event(Date date, String text, long id) {
            this.date = date;
            this.text = text;
            this.id = id;
        }

        @Override
        public String toString() {
            return date.toString() + " " + text;
        }
    }


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
        sendMessage.setChatId(message.getChatId().toString());
        sendMessage.setText(text);
        try {
            execute(sendMessage);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();

        if (message != null && message.hasText()) {
            String messageText = message.getText();
            if (messageText.endsWith(botName)) {
                messageText = messageText.substring(0, messageText.length() - botName.length());
            }
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
                    try {
                        show(message, false);
                    } catch (BotException e) {
                        //ignoring
                    }
                    currentCommand = Command.NONE;
                    break;
                case "/delete":
                    currentCommand = Command.DELETE;
                    try {
                        show(message, true);
                        sendMsg(message, "Please send me the number of the event you want to delete");
                    } catch (Exception e) {
                        //ignoring
                    }
                    break;
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
                                if (add(message.getChatId(), messageText)) {
                                    sendMsg(message, "Added successfully!");
                                    currentCommand = Command.NONE;
                                } else {
                                    sendMsg(message, "Invalid date format.\nFormat should be: DD.MM.YYYY\nTry again");
                                }
                                break;
                            case DELETE:
                                if (delete(messageText)) {
                                    sendMsg(message, "deleted successfully!");
                                    currentCommand = Command.NONE;
                                } else {
                                    sendMsg(message, "Something wrong\nTry again");

                                }
                                break;
                            default:
                                break;
                        }
                    }
                    break;
            }
        }
    }

    private Event check(String text) throws Exception {
        String[] tokens = text.trim().split(" ");
        if (tokens.length == 0) throw new BotException("error");
        String date = tokens[0];
        date = date.trim();
        boolean dateGood = date.matches("[0-9][0-9].[0-9][0-9].[0-9][0-9][0-9][0-9]");
        if (!dateGood) throw new BotException("error");
        String day = date.substring(0, 2);
        String month = date.substring(3, 5);
        String year = date.substring(6, 10);
        if (Integer.parseInt(day) > 31 || Integer.parseInt(month) > 12 || Integer.parseInt(year) > 2100)
            throw new BotException("error");
        if (tokens.length < 2 || tokens[1].length() == 0) throw new BotException("event of length 0");
        return new Event(Date.valueOf(year + "-" + month + "-" + day), text.substring(11), -1);
    }

    private String getQuery(boolean deleteFlag) {
        if (deleteFlag) return "SELECT * FROM Events WHERE chatId=? ORDER BY id";
        return "SELECT * FROM Events WHERE chatId=? ORDER BY date";
    }

    private boolean add(Long chatId, String text) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Event event = check(text);
            try (Connection connection = DriverManager.getConnection(connectionURL, userName, passWord);
                 PreparedStatement statement = connection.prepareStatement("INSERT INTO `Events` (`chatId`, `text`, `date`) VALUES (?,?,?)")) {
                statement.setLong(1, chatId);
                statement.setString(2, event.text);
                statement.setDate(3, event.date);
                if (statement.executeUpdate() != 1) {
                    return false;
                }

            } catch (SQLException e) {
                return false;

            }

        } catch (Exception e) {
            return false;
        }
        return true;
    }


    private void show(Message message, boolean deleteFlag) throws BotException {
        //  it will show all the events
        try {
            StringBuilder stringBuilder = new StringBuilder();
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection connection = DriverManager.getConnection(connectionURL, userName, passWord);
                 PreparedStatement statement = connection.prepareStatement(getQuery(deleteFlag))) {
                statement.setLong(1, message.getChatId());

                try (ResultSet resultSet = statement.executeQuery()) {
                    Event event;
                    while ((event = toEvent(statement.getMetaData(), resultSet)) != null) {
                        if (deleteFlag) {
                            stringBuilder.append("Event number ").append(event.id).append(": ");
                        }
                        stringBuilder.append(event.toString()).append("\n");
                    }
                } catch (Exception e) {
                    sendMsg(message, "Something wrong. Try again");
                }

                if (stringBuilder.toString().equals("")) {
                    sendMsg(message, "No events, you're all free!");
                    if (deleteFlag) throw new BotException("nothing to delete");
                } else {
                    sendMsg(message, stringBuilder.toString());
                }


            } catch (SQLException e) {
                if (!deleteFlag)
                    sendMsg(message, "Something wrong. Try again");
                else throw new BotException("error");

            }

        } catch (Exception e) {
            if (!deleteFlag)
                sendMsg(message, "Something wrong. Try again");
            else throw new BotException("error");
        }


    }


    private boolean delete(String s) {

        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            long id = Long.parseLong(s);
            try (Connection connection = DriverManager.getConnection(connectionURL, userName, passWord);
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM `Events` WHERE `id` = ?")) {
                statement.setLong(1, id);
                if (statement.executeUpdate() != 1) {
                    return false;
                }

            } catch (SQLException e) {
                return false;

            }

        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private Event toEvent(ResultSetMetaData metaData, ResultSet resultSet) throws SQLException {
        if (!resultSet.next()) {
            return null;
        }
        Date date = null;
        String event = "";
        long id = -1;
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            switch (metaData.getColumnName(i)) {
                case "text":
                    event = resultSet.getString(i);
                    break;
                case "date":
                    date = resultSet.getDate(i);
                    break;
                case "id":
                    id = resultSet.getLong(i);
                default:
                    // No operations.
            }
        }

        return new Event(date, event, id);
    }

    public String getBotUsername() {
        return botName.substring(1);
    }

    public String getBotToken() {
        return "1043383878:AAFMtEM1RTsLAoSiu-GbNcAPZJMixrjtCws";
    }

}
