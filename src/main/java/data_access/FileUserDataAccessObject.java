package data_access;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import entity.User;
import entity.UserFactory;
import okhttp3.*;
import org.json.JSONException;
import org.json.JSONObject;
import use_case.change_password.ChangePasswordUserDataAccessInterface;
import use_case.login.LoginUserDataAccessInterface;
import use_case.signup.SignupUserDataAccessInterface;

/**
 * DAO for user data implemented using a File to persist the data.
 */
public class FileUserDataAccessObject implements SignupUserDataAccessInterface,
                                                 LoginUserDataAccessInterface,
                                                 ChangePasswordUserDataAccessInterface {

    private static final String HEADER = "username,password";

    private final File csvFile;
    private final Map<String, Integer> headers = new LinkedHashMap<>();
    private final Map<String, User> accounts = new HashMap<>();

    public FileUserDataAccessObject(String csvPath, UserFactory userFactory) throws IOException {

        csvFile = new File(csvPath);
        headers.put("username", 0);
        headers.put("password", 1);

        if (csvFile.length() == 0) {
            save();
        }
        else {

            try (BufferedReader reader = new BufferedReader(new FileReader(csvFile))) {
                final String header = reader.readLine();

                if (!header.equals(HEADER)) {
                    throw new RuntimeException(String.format("header should be%n: %s%but was:%n%s", HEADER, header));
                }

                String row;
                while ((row = reader.readLine()) != null) {
                    final String[] col = row.split(",");
                    final String username = String.valueOf(col[headers.get("username")]);
                    final String password = String.valueOf(col[headers.get("password")]);
                    final User user = userFactory.create(username, password);
                    accounts.put(username, user);
                }
            }
        }
    }

    private void save() {
        final BufferedWriter writer;
        try {
            writer = new BufferedWriter(new FileWriter(csvFile));
            writer.write(String.join(",", headers.keySet()));
            writer.newLine();

            for (User user : accounts.values()) {
                final String line = String.format("%s,%s",
                        user.getName(), user.getPassword());
                writer.write(line);
                writer.newLine();
            }

            writer.close();

        }
        catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void save(User user) {
        accounts.put(user.getName(), user);
        this.save();
    }

    @Override
    public User get(String username) {
        return accounts.get(username);
    }

    @Override
    public void setCurrentUser(String name);

    @Override
    public boolean existsByName(String identifier) {
        return accounts.containsKey(identifier);
    }

    @Override
    public void changePassword(User user) {
        // Replace the User object in the map
        accounts.put(user.getName(), user);
        save();
    }

    /**
     * The DAO for user data.
     */
    public static class DBUserDataAccessObject implements SignupUserDataAccessInterface,
                                                   LoginUserDataAccessInterface,
                                                   ChangePasswordUserDataAccessInterface {
        private static final int SUCCESS_CODE = 200;
        private static final String CONTENT_TYPE_LABEL = "Content-Type";
        private static final String CONTENT_TYPE_JSON = "application/json";
        private static final String STATUS_CODE_LABEL = "status_code";
        private static final String USERNAME = "username";
        private static final String PASSWORD = "password";
        private static final String MESSAGE = "message";
        private final UserFactory userFactory;

        public DBUserDataAccessObject(UserFactory userFactory) {
            this.userFactory = userFactory;
            // No need to do anything to reinitialize a user list! The data is the cloud that may be miles away.
        }

        @Override
        public User get(String username) {
            // Make an API call to get the user object.
            final OkHttpClient client = new OkHttpClient().newBuilder().build();
            final Request request = new Request.Builder()
                    .url(String.format("http://vm003.teach.cs.toronto.edu:20112/user?username=%s", username))
                    .addHeader("Content-Type", CONTENT_TYPE_JSON)
                    .build();
            try {
                final Response response = client.newCall(request).execute();

                final JSONObject responseBody = new JSONObject(response.body().string());

                if (responseBody.getInt(STATUS_CODE_LABEL) == SUCCESS_CODE) {
                    final JSONObject userJSONObject = responseBody.getJSONObject("user");
                    final String name = userJSONObject.getString(USERNAME);
                    final String password = userJSONObject.getString(PASSWORD);

                    return userFactory.create(name, password);
                }
                else {
                    throw new RuntimeException(responseBody.getString(MESSAGE));
                }
            }
            catch (IOException | JSONException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void setCurrentUser(String name) {
            ;
        }

        @Override
        public boolean existsByName(String username) {
            final OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            final Request request = new Request.Builder()
                    .url(String.format("http://vm003.teach.cs.toronto.edu:20112/checkIfUserExists?username=%s", username))
                    .addHeader(CONTENT_TYPE_LABEL, CONTENT_TYPE_JSON)
                    .build();
            try {
                final Response response = client.newCall(request).execute();

                final JSONObject responseBody = new JSONObject(response.body().string());

                //                throw new RuntimeException(responseBody.getString("message"));
                return responseBody.getInt(STATUS_CODE_LABEL) == SUCCESS_CODE;
            }
            catch (IOException | JSONException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void save(User user) {
            final OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();

            // POST METHOD
            final MediaType mediaType = MediaType.parse(CONTENT_TYPE_JSON);
            final JSONObject requestBody = new JSONObject();
            requestBody.put(USERNAME, user.getName());
            requestBody.put(PASSWORD, user.getPassword());
            final RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
            final Request request = new Request.Builder()
                    .url("http://vm003.teach.cs.toronto.edu:20112/user")
                    .method("POST", body)
                    .addHeader(CONTENT_TYPE_LABEL, CONTENT_TYPE_JSON)
                    .build();
            try {
                final Response response = client.newCall(request).execute();

                final JSONObject responseBody = new JSONObject(response.body().string());

                if (responseBody.getInt(STATUS_CODE_LABEL) == SUCCESS_CODE) {
                    // success!
                }
                else {
                    throw new RuntimeException(responseBody.getString(MESSAGE));
                }
            }
            catch (IOException | JSONException ex) {
                throw new RuntimeException(ex);
            }
        }

        @Override
        public void changePassword(User user) {
            final OkHttpClient client = new OkHttpClient().newBuilder()
                                            .build();

            // POST METHOD
            final MediaType mediaType = MediaType.parse(CONTENT_TYPE_JSON);
            final JSONObject requestBody = new JSONObject();
            requestBody.put(USERNAME, user.getName());
            requestBody.put(PASSWORD, user.getPassword());
            final RequestBody body = RequestBody.create(requestBody.toString(), mediaType);
            final Request request = new Request.Builder()
                                        .url("http://vm003.teach.cs.toronto.edu:20112/user")
                                        .method("PUT", body)
                                        .addHeader(CONTENT_TYPE_LABEL, CONTENT_TYPE_JSON)
                                        .build();
            try {
                final Response response = client.newCall(request).execute();

                final JSONObject responseBody = new JSONObject(response.body().string());

                if (responseBody.getInt(STATUS_CODE_LABEL) == SUCCESS_CODE) {
                    // success!
                }
                else {
                    throw new RuntimeException(responseBody.getString(MESSAGE));
                }
            }
            catch (IOException | JSONException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}
