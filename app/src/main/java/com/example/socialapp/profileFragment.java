package com.example.socialapp;

import android.content.Context;
import android.content.CursorLoader;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.socialapp.R;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.InputFile;
import io.appwrite.services.Account;
import io.appwrite.services.Storage;

public class profileFragment extends Fragment {

    private NavController navController;
    private ImageView photoImageView;
    private TextView displayNameTextView, emailTextView;
    private Client client;
    private ActivityResultLauncher<String> profileImagePicker;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflar el layout para este fragment
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = Navigation.findNavController(view);
        photoImageView = view.findViewById(R.id.photoImageView);
        displayNameTextView = view.findViewById(R.id.displayNameTextView);
        emailTextView = view.findViewById(R.id.emailTextView);

        // Inicializamos el cliente Appwrite
        client = new Client(requireContext())
                .setProject(getString(R.string.APPWRITE_PROJECT_ID));

        // Configuramos el lanzador para seleccionar imagen de perfil
        profileImagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) {
                uploadProfilePhoto(uri);
            }
        });

        // Al pulsar la imagen se abre la galería para seleccionar una nueva foto de perfil
        photoImageView.setOnClickListener(v -> profileImagePicker.launch("image/*"));

        Account account = new Account(client);
        Handler mainHandler = new Handler(Looper.getMainLooper());

        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                // Muestra el nombre y el correo del usuario
                displayNameTextView.setText(result.getName().toString());
                emailTextView.setText(result.getEmail().toString());

                // Recuperar la URL de la foto de perfil guardada en SharedPreferences
                SharedPreferences prefs = requireContext().getSharedPreferences("profile", Context.MODE_PRIVATE);
                String storedProfilePhotoUrl = prefs.getString("profilePhotoUrl", null);

                // Actualizar la UI en el hilo principal
                mainHandler.post(() -> {
                    if (storedProfilePhotoUrl != null) {
                        Glide.with(requireContext())
                                .load(storedProfilePhotoUrl)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)  // Evita usar caché
                                .skipMemoryCache(true)  // Obliga a recargar la imagen
                                .circleCrop()
                                .into(photoImageView);
                    } else {
                        // Si no hay imagen guardada, cargar la predeterminada
                        Glide.with(requireContext())
                                .load(R.drawable.user)
                                .circleCrop()
                                .into(photoImageView);
                    }
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Sube la imagen seleccionada al bucket de perfiles y actualiza el ImageView.
     */
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private void uploadProfilePhoto(Uri uri) {
        Storage storage = new Storage(client);
        File tempFile;
        try {
            tempFile = getFileFromUri(requireContext(), uri);
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
        storage.createFile(
                getString(R.string.APPWRITE_PROFILE_BUCKET_ID),
                "unique()",
                InputFile.Companion.fromFile(tempFile),
                new ArrayList<>(),
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        error.printStackTrace();
                        return;
                    }
                    String downloadUrl = client.getEndpoint() + "/storage/buckets/" +
                            getString(R.string.APPWRITE_PROFILE_BUCKET_ID) +
                            "/files/" + result.getId() + "/view?project=" +
                            getString(R.string.APPWRITE_PROJECT_ID);

                    mainHandler.post(() -> {  // 🔹 Actualiza en el hilo principal
                        Glide.with(requireContext())
                                .load(downloadUrl)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .into(photoImageView);

                        requireContext().getSharedPreferences("profile", Context.MODE_PRIVATE)
                                .edit()
                                .putString("profilePhotoUrl", downloadUrl)
                                .apply();
                    });
                })
        );
    }


    /**
     * Convierte un Uri en un archivo temporal para poder subirlo.
     */
    private File getFileFromUri(Context context, Uri uri) throws Exception {
        InputStream inputStream = context.getContentResolver().openInputStream(uri);
        if (inputStream == null) {
            throw new FileNotFoundException("No se pudo abrir el URI: " + uri);
        }
        String fileName = getFileName(context, uri);
        File tempFile = new File(context.getCacheDir(), fileName);
        FileOutputStream outputStream = new FileOutputStream(tempFile);
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, length);
        }
        outputStream.close();
        inputStream.close();
        return tempFile;
    }

    /**
     * Obtiene el nombre del archivo a partir del Uri.
     */
    private String getFileName(Context context, Uri uri) {
        String fileName = "temp_file";
        try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    fileName = cursor.getString(nameIndex);
                }
            }
        }
        return fileName;
    }
}
