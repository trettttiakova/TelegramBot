import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.sql.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.Month;
import java.util.concurrent.ThreadLocalRandom;

import static jdk.nashorn.internal.runtime.JSType.isNumber;

public class Bot extends TelegramLongPollingBot {
    private final String userName = "root";
    private static final String passWord = "root";
    private final String connectionURL = "jdbc:mysql://localhost:3306/MyDB?useUnicode=true&characterSetResults=UTF-8&" +
            "characterEncoding=UTF-8&useTimezone=true&serverTimezone=Europe/Moscow";
    private final String botName = "@YourCalendarBot";

    private enum Command {
        CALENDAR,
        ADD,
        DELETE,
        RANDOM,
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
        try {
            if (message != null && message.hasText()) {
                String messageText = message.getText();
                if (messageText.endsWith(botName)) {
                    messageText = messageText.substring(0, messageText.length() - botName.length());
                }
                switch (messageText) {
                    case "/start":
                        sendMsg(message, "I'm the Calendar Bot! I can do stuff!\n" +
                                "Try one of my commands:" +
                                "\n/calendar - show calendar for month" +
                                "\n/add - add new event" +
                                "\n/show - show all your events" +
                                "\n/delete - delete event" +
                                "\n/random - generate a random date just for fun" +
                                "\n/stop - stop command");
                        currentCommand = Command.NONE;
                        break;
                    case "/calendar":
                        currentCommand = Command.CALENDAR;
                        sendMsg(message, "Send me month like this: MM.YYYY");
                        break;
                    case "/add":
                        currentCommand = Command.ADD;
                        sendMsg(message, "Send me the date and the event like this: DD.MM.YYYY your event");
                        break;
                    case "/show":
                        show(message, false);
                        currentCommand = Command.NONE;
                        break;
                    case "/delete":
                        currentCommand = Command.DELETE;
                        show(message, true);
                        sendMsg(message, "Please send me the number of the event you want to delete");
                        break;
                    case "/random":
                        currentCommand = Command.RANDOM;
                        sendMsg(message, "Send me dates like this: DD.MM.YYYY DD.MM.YYYY\n" +
                                "The first date should be earlier than the second date");
                        break;
                    case "/stop":
                        currentCommand = Command.NONE;
                        sendMsg(message, "Ready for the new command!");
                        break;
                    default:
                        switch (currentCommand) {
                            case NONE:
                                sendMsg(message, "I don't know what you mean :(\n" +
                                        "Try one of these: " +
                                        "\n/start" +
                                        "\n/calendar" +
                                        "\n/add" +
                                        "\n/show" +
                                        "\n/delete" +
                                        "\n/random" +
                                        "\n/stop");
                                break;
                            case CALENDAR:
                                calendar(message);
                                break;
                            case ADD:
                                add(message);
                                break;
                            case DELETE:
                                delete(message);
                                break;
                            case RANDOM:
                                random(message);
                                break;
                            default:
                                break;
                        }
                        currentCommand = Command.NONE;
                        break;
                }
            }
        } catch (BotException e) {
            sendMsg(message, e.getMessage());
        }
    }

    private String getQuery(boolean deleteFlag) {
        if (deleteFlag) return "SELECT * FROM Events WHERE chatId=? ORDER BY id";
        return "SELECT * FROM Events WHERE chatId=? ORDER BY date";
    }

    private java.util.Date correctDateFormat(String date) throws BotException {
        SimpleDateFormat format = new SimpleDateFormat("dd.MM.yyyy");
        format.setLenient(false);
        try {
            return format.parse(date);
        } catch (ParseException e) {
            throw new BotException("Invalid date format.\nFormat should be: DD.MM.YYYY\nTry again");
        }
    }

    private void calendar(Message message) throws BotException {
        correctDateFormat("01." + message.getText());
        String[] selectedMonth = message.getText().split("\\.");
        LocalDate date = LocalDate.of(Integer.parseInt(selectedMonth[1]), Integer.parseInt(selectedMonth[0]), 1);
        date = date.minusDays(date.getDayOfMonth() - 1);
        Month month = date.getMonth();
        int firstWeekDay = date.getDayOfWeek().getValue() - 1;
        StringBuilder calendar = new StringBuilder();
        calendar.append(month.toString()).append(" ").append(date.getYear()).append("\n");
        for (DayOfWeek weekday : DayOfWeek.values()) {
            calendar.append(weekday.name(), 0, 2).append(" ");
        }
        calendar.append("\n");
        for (int day = 0; day < firstWeekDay; ++day) {
            if (day % 2 == 0) {
                calendar.append(" ");
            }
            calendar.append("      ");
        }
        for (int day = 1; day <= month.length(date.isLeapYear()); ++day) {
            calendar.append(String.format("%02d", day)).append("  ");
            if ((day + firstWeekDay) % 7 == 0) {
                calendar.append("\n");
            }
        }
        sendMsg(message, String.valueOf(calendar));
    }

    private void add(Message message) throws BotException {
        String[] tokens = message.getText().trim().split(" ");
        if (tokens.length < 2) {
            throw new BotException("Invalid event format.\nFormat should be: DD.MM.YYYY your event\nTry again");
        }
        Event event = new Event((Date) correctDateFormat(tokens[0]), message.getText().substring(11), -1);
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new BotException("Unable to enter event now.\nPlease try again later");
        }
        try (Connection connection = DriverManager.getConnection(connectionURL, userName, passWord);
             PreparedStatement statement = connection.prepareStatement
                     ("INSERT INTO `Events` (`chatId`, `text`, `date`) VALUES (?,?,?)")) {
            statement.setLong(1, message.getChatId());
            statement.setString(2, event.text);
            statement.setDate(3, event.date);
            if (statement.executeUpdate() != 1) {
                throw new BotException("Unable to enter event now.\nPlease try again later");
            }
            sendMsg(message, "Added successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BotException("Unable to enter event now.\nPlease try again later");
        }
    }

    private void show(Message message, boolean deleteFlag) throws BotException {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new BotException("Unable to" + (deleteFlag ? "delete" : "show") + "event now.\nPlease try again later");
        }
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
            } catch (SQLException e) {
                e.printStackTrace();
                throw new BotException("Unable to" + (deleteFlag ? "delete" : "show") + "event now.\nPlease try again later");
            }

            if (stringBuilder.toString().equals("")) {
                if (deleteFlag) {
                    sendMsg(message, "Nothing to delete");
                } else {
                    sendMsg(message, "No events, you're all free!");
                }
            } else {
                sendMsg(message, stringBuilder.toString());
            }

        } catch (SQLException e) {
            e.printStackTrace();
            throw new BotException("Unable to" + (deleteFlag ? "delete" : "show") + "event now.\nPlease try again later");
        }
    }

    private void delete(Message message) throws BotException {
        if (!isNumber(message.getText())) {
            throw new BotException("Invalid number format.\nTry again");
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
            throw new BotException("Unable to delete event now.\nPlease try again later");
        }
        long id = Long.parseLong(message.getText());
        try (Connection connection = DriverManager.getConnection(connectionURL, userName, passWord);
             PreparedStatement statement = connection.prepareStatement("DELETE FROM `Events` WHERE `id` = ?")) {
            statement.setLong(1, id);
            if (statement.executeUpdate() != 1) {
                throw new BotException("Unable to delete event now.\nPlease try again later");
            }
            sendMsg(message, "Deleted successfully!");
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BotException("Unable to delete event now.\nPlease try again later");
        }
    }

    private void random(Message message) throws BotException {
        String[] dates = message.getText().split(" ");
        if (dates.length != 2) {
            throw new BotException("Invalid dates format.\nFormat should be: DD.MM.YYYY DD.MM.YYYY\nTry again");
        }
        java.util.Date firstDate = correctDateFormat(dates[0]);
        java.util.Date secondDate = correctDateFormat(dates[1]);
        if (!firstDate.before(secondDate)) {
            throw new BotException("The first date should be earlier than the second date\nTry again");
        }
        sendMsg(message, (new SimpleDateFormat("dd.MM.yyyy")).format
                (new Date(ThreadLocalRandom.current().nextLong(firstDate.getTime(), secondDate.getTime()))));
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
