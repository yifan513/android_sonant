/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.shenxunchat.sonant.servers;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import se.lublin.humla.model.Server;
import com.shenxunchat.sonant.R;
import com.shenxunchat.sonant.Settings;
import com.shenxunchat.sonant.db.DatabaseProvider;
import com.shenxunchat.sonant.db.PublicServer;

/**
 * Displays a list of servers, and allows the user to connect and edit them.
 * @author morlunk
 *
 */
public class FavouriteServerListFragment extends Fragment implements OnItemClickListener, FavouriteServerAdapter.FavouriteServerAdapterMenuListener {

    private ServerConnectHandler mConnectHandler;
    private DatabaseProvider mDatabaseProvider;
    private GridView mServerGrid;
    private ServerAdapter<Server> mServerAdapter;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);

        try {
            mConnectHandler = (ServerConnectHandler)activity;
            mDatabaseProvider = (DatabaseProvider) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()+" must implement ServerConnectHandler!");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_list, container, false);
        mServerGrid = (GridView) view.findViewById(R.id.server_list_grid);
        mServerGrid.setOnItemClickListener(this);
        mServerGrid.setEmptyView(view.findViewById(R.id.server_list_grid_empty));

        registerForContextMenu(mServerGrid);
        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_server_list, menu);
    }

    @Override
    public void onResume() {
        super.onResume();
        updateServers();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_import_servers) {
            showImportServersDialog();
            return true;
        } else if (itemId == R.id.menu_add_server_item) {
            addServer();
            return true;
        } else if (itemId == R.id.menu_quick_connect) {
            ServerEditFragment.createServerEditDialog(getActivity(), null, ServerEditFragment.Action.CONNECT_ACTION, true)
                    .show(getFragmentManager(), "serverInfo");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void showImportServersDialog() {
        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_server_import, null, false);
        EditText jsonInput = view.findViewById(R.id.server_import_json);

        AlertDialog dialog = new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.import_servers_title)
                .setView(view)
                .setPositiveButton(R.string.import_servers, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(ignored -> {
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
            }

            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                String json = normalizeJsonInput(jsonInput.getText().toString());
                if (json.isEmpty()) {
                    jsonInput.setError(getString(R.string.import_servers_empty));
                    return;
                }

                try {
                    List<Server> servers = parseServers(json);
                    for (Server server : servers) {
                        mDatabaseProvider.getDatabase().addServer(server);
                    }
                    updateServers();
                    Toast.makeText(requireContext(),
                            getResources().getQuantityString(
                                    R.plurals.import_servers_success,
                                    servers.size(), servers.size()),
                            Toast.LENGTH_LONG).show();
                    dialog.dismiss();
                } catch (ServerImportException e) {
                    jsonInput.setError(e.getMessage());
                } catch (JSONException e) {
                    jsonInput.setError(getString(
                            R.string.import_servers_invalid_json, e.getMessage()));
                }
            });
        });
        dialog.show();
    }

    private String normalizeJsonInput(String input) {
        String json = input
                .replace("\uFEFF", "")
                .replace('\u00A0', ' ')
                .trim();

        int openingFence = json.indexOf("```");
        if (openingFence >= 0) {
            int firstLineEnd = json.indexOf('\n', openingFence);
            if (firstLineEnd >= 0) {
                json = json.substring(firstLineEnd + 1);
            }
            int closingFence = json.lastIndexOf("```");
            if (closingFence >= 0) {
                json = json.substring(0, closingFence);
            }
            json = json.trim();
        }

        int objectStart = json.indexOf('{');
        int arrayStart = json.indexOf('[');
        int jsonStart;
        if (objectStart < 0) {
            jsonStart = arrayStart;
        } else if (arrayStart < 0) {
            jsonStart = objectStart;
        } else {
            jsonStart = Math.min(objectStart, arrayStart);
        }
        if (jsonStart > 0) {
            json = json.substring(jsonStart);
        }

        if (json.startsWith("{")) {
            int jsonEnd = json.lastIndexOf('}');
            if (jsonEnd >= 0) {
                json = json.substring(0, jsonEnd + 1);
            }
        } else if (json.startsWith("[")) {
            int jsonEnd = json.lastIndexOf(']');
            if (jsonEnd >= 0) {
                json = json.substring(0, jsonEnd + 1);
            }
        }
        return json;
    }

    private List<Server> parseServers(String json) throws JSONException, ServerImportException {
        JSONArray array;
        if (json.startsWith("[")) {
            array = new JSONArray(json);
        } else {
            JSONObject root = new JSONObject(json);
            array = root.optJSONArray("servers");
            if (array == null) {
                throw new JSONException("Expected a JSON array or an object containing \"servers\"");
            }
        }

        if (array.length() == 0) {
            throw new ServerImportException(getString(R.string.import_servers_empty));
        }

        String defaultUsername = Settings.getInstance(requireContext()).getDefaultUsername();
        List<Server> servers = new ArrayList<>(array.length());
        for (int i = 0; i < array.length(); i++) {
            JSONObject item = array.optJSONObject(i);
            if (item == null) {
                throw new JSONException("Server " + (i + 1) + " must be a JSON object");
            }

            String host = item.optString("hostname", item.optString("host", "")).trim();
            if (host.isEmpty()) {
                throw new ServerImportException(
                        getString(R.string.import_servers_missing_host, i + 1));
            }

            String name = item.optString("name", item.optString("label", host)).trim();
            if (name.isEmpty()) {
                name = host;
            }

            int port = 0;
            if (item.has("port") && !item.isNull("port")) {
                try {
                    port = Integer.parseInt(String.valueOf(item.get("port")));
                } catch (NumberFormatException e) {
                    throw new ServerImportException(
                            getString(R.string.import_servers_invalid_port, i + 1));
                }
                if (port < 1 || port > 65535) {
                    throw new ServerImportException(
                            getString(R.string.import_servers_invalid_port, i + 1));
                }
            }

            String username = item.optString("username", defaultUsername).trim();
            if (username.isEmpty()) {
                username = defaultUsername;
            }
            String password = item.optString("password", "");
            servers.add(new Server(-1, name, host, port, username, password));
        }
        return servers;
    }

    private static class ServerImportException extends Exception {
        private static final long serialVersionUID = 1L;

        ServerImportException(String message) {
            super(message);
        }
    }

    public void addServer() {
        ServerEditFragment.createServerEditDialog(getActivity(), null, ServerEditFragment.Action.ADD_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void editServer(Server server) {
        ServerEditFragment.createServerEditDialog(getActivity(), server, ServerEditFragment.Action.EDIT_ACTION, false)
                .show(getFragmentManager(), "serverInfo");
    }

    public void shareServer(Server server) {
        // Build Mumble server URL
        String serverUrl = "mumble://" + server.getHost()
            + (server.getPort() == 0 ? "" : ":" + server.getPort()) + "/";

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.putExtra(Intent.EXTRA_TEXT, getString(R.string.shareMessage, serverUrl));
        intent.setType("text/plain");
        startActivity(intent);
    }

    public void deleteServer(final Server server) {
        new MaterialAlertDialogBuilder(requireContext())
                .setMessage(R.string.confirm_delete_server)
                .setPositiveButton(R.string.delete, (dialog, which) -> {
                    mDatabaseProvider.getDatabase().removeServer(server);
                    mServerAdapter.remove(server);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public void updateServers() {
        List<Server> servers = getServers();
        mServerAdapter = new FavouriteServerAdapter(getActivity(), servers, this);
        mServerGrid.setAdapter(mServerAdapter);
    }

    public List<Server> getServers() {
        List<Server> servers = mDatabaseProvider.getDatabase().getServers();
        return servers;
    }

    @Override
    public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3) {
        mConnectHandler.connectToServer(mServerAdapter.getItem(arg2));
    }

    public static interface ServerConnectHandler {
        public void connectToServer(Server server);
        public void connectToPublicServer(PublicServer server);
    }
}
