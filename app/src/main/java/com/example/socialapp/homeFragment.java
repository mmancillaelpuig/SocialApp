package com.example.socialapp;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.socialapp.R;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.appwrite.Client;
import io.appwrite.coroutines.CoroutineCallback;
import io.appwrite.exceptions.AppwriteException;
import io.appwrite.models.DocumentList;
import io.appwrite.services.Account;
import io.appwrite.services.Databases;
import io.appwrite.services.Storage;

public class homeFragment extends Fragment {

    private PostsAdapter adapter;
    private ImageView headerPhotoImageView;
    private TextView headerDisplayNameTextView, headerEmailTextView;
    private Client client;
    private Account account;
    private String userId;
    private String storedProfilePhotoUrl; // URL guardada del perfil del usuario
    private NavController navController;
    private AppViewModel appViewModel;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public homeFragment() {}

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        navController = Navigation.findNavController(view);

        // Configuramos el header de la NavigationView
        NavigationView navigationView = view.getRootView().findViewById(R.id.nav_view);
        View header = navigationView.getHeaderView(0);
        headerPhotoImageView = header.findViewById(R.id.imageView);
        headerDisplayNameTextView = header.findViewById(R.id.displayNameTextView);
        headerEmailTextView = header.findViewById(R.id.emailTextView);

        client = new Client(requireContext())
                .setProject(getString(R.string.APPWRITE_PROJECT_ID));
        account = new Account(client);

        loadUserData();

        view.findViewById(R.id.gotoNewPostFragmentButton).setOnClickListener(v ->
                navController.navigate(R.id.newFragmentPost)
        );

        RecyclerView postsRecyclerView = view.findViewById(R.id.postsRecyclerView);
        adapter = new PostsAdapter();
        postsRecyclerView.setAdapter(adapter);

        appViewModel = new ViewModelProvider(requireActivity()).get(AppViewModel.class);
    }

    /**
     * Recupera los datos del usuario actual, actualiza el header y guarda la foto de perfil
     * desde SharedPreferences si ya fue actualizada en profileFragment.
     */
    private void loadUserData() {
        try {
            account.get(new CoroutineCallback<>((result, error) -> {
                if (error != null) {
                    error.printStackTrace();
                    return;
                }
                mainHandler.post(() -> {
                    userId = result.getId();
                    headerDisplayNameTextView.setText(result.getName().toString());
                    headerEmailTextView.setText(result.getEmail().toString());

                    // Recuperar la foto de perfil desde SharedPreferences
                    storedProfilePhotoUrl = requireContext()
                            .getSharedPreferences("profile", Context.MODE_PRIVATE)
                            .getString("profilePhotoUrl", null);

                    if (storedProfilePhotoUrl != null) {
                        Glide.with(requireContext())
                                .load(storedProfilePhotoUrl)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .circleCrop()
                                .into(headerPhotoImageView);
                    } else {
                        Glide.with(requireContext())
                                .load(R.drawable.user)
                                .circleCrop()
                                .into(headerPhotoImageView);
                    }

                    obtenerPosts();
                });
            }));
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }


    /**
     * Adapter para mostrar los posts.
     */
    class PostsAdapter extends RecyclerView.Adapter<PostsAdapter.PostViewHolder> {

        private DocumentList<Map<String, Object>> lista = null;

        class PostViewHolder extends RecyclerView.ViewHolder {
            ImageView authorPhotoImageView, likeImageView, mediaImageView, deleteImageView, shareImageView;
            TextView authorTextView, contentTextView, numLikesTextView;

            PostViewHolder(@NonNull View itemView) {
                super(itemView);
                authorPhotoImageView = itemView.findViewById(R.id.authorPhotoImageView);
                likeImageView = itemView.findViewById(R.id.likeImageView);
                authorTextView = itemView.findViewById(R.id.authorTextView);
                contentTextView = itemView.findViewById(R.id.contentTextView);
                mediaImageView = itemView.findViewById(R.id.mediaImage);
                numLikesTextView = itemView.findViewById(R.id.numLikesTextView);
                deleteImageView = itemView.findViewById(R.id.deleteImageView);
                shareImageView = itemView.findViewById(R.id.shareImageView);
            }
        }

        @NonNull
        @Override
        public PostViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.viewholder_post, parent, false);
            return new PostViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull PostViewHolder holder, int position) {
            Map<String, Object> post = lista.getDocuments().get(position).getData();
            String postId = lista.getDocuments().get(position).getId();

            // Mostrar la foto de perfil del autor:
            // Si el campo authorPhotoUrl viene nulo y el post es del usuario actual,
            // se utiliza la foto guardada en SharedPreferences (storedProfilePhotoUrl)
            if (post.get("authorPhotoUrl") == null) {
                if (userId.equals(post.get("uid"))) {
                    if (storedProfilePhotoUrl != null) {
                        Glide.with(getContext())
                                .load(storedProfilePhotoUrl)
                                .diskCacheStrategy(DiskCacheStrategy.NONE)
                                .skipMemoryCache(true)
                                .circleCrop()
                                .into(holder.authorPhotoImageView);
                    } else {
                        holder.authorPhotoImageView.setImageResource(R.drawable.user);
                    }
                } else {
                    holder.authorPhotoImageView.setImageResource(R.drawable.user);
                }
            } else {
                Glide.with(getContext())
                        .load(post.get("authorPhotoUrl").toString())
                        .diskCacheStrategy(DiskCacheStrategy.NONE)
                        .skipMemoryCache(true)
                        .circleCrop()
                        .into(holder.authorPhotoImageView);
            }

            holder.authorTextView.setText(post.get("author").toString());
            holder.contentTextView.setText(post.get("content").toString());

            // Configuración de la miniatura de media, likes, eliminar, compartir...
            if (post.get("mediaUrl") != null) {
                holder.mediaImageView.setVisibility(View.VISIBLE);
                if ("audio".equals(post.get("mediaType").toString())) {
                    Glide.with(requireView())
                            .load(R.drawable.audio)
                            .centerCrop()
                            .into(holder.mediaImageView);
                } else {
                    Glide.with(requireView())
                            .load(post.get("mediaUrl").toString())
                            .centerCrop()
                            .into(holder.mediaImageView);
                }
                holder.mediaImageView.setOnClickListener(view -> {
                    appViewModel.postSeleccionado.setValue(post);
                    navController.navigate(R.id.mediaFragment);
                });
            } else {
                holder.mediaImageView.setVisibility(View.GONE);
            }

            // Gestión de likes
            List<String> likes = (List<String>) post.get("likes");
            if(likes.contains(userId))
                holder.likeImageView.setImageResource(R.drawable.like_on);
            else
                holder.likeImageView.setImageResource(R.drawable.like_off);
            holder.numLikesTextView.setText(String.valueOf(likes.size()));

            // Botón eliminar (solo para posts del usuario)
            if(userId.equals(post.get("uid"))) {
                holder.deleteImageView.setVisibility(View.VISIBLE);
                holder.deleteImageView.setOnClickListener(v -> {
                    new AlertDialog.Builder(requireContext())
                            .setTitle("Eliminar post")
                            .setMessage("¿Estás seguro de querer eliminar este post?")
                            .setPositiveButton("Eliminar", (dialog, which) -> eliminarPost(postId))
                            .setNegativeButton("Cancelar", null)
                            .show();
                });
            } else {
                holder.deleteImageView.setVisibility(View.GONE);
            }

            // Botón compartir
            holder.shareImageView.setOnClickListener(v -> {
                String contenido = post.get("content").toString();
                String mediaUrl = post.get("mediaUrl") != null ? post.get("mediaUrl").toString() : "";

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_TEXT, contenido + (mediaUrl.isEmpty() ? "" : "\n\n" + mediaUrl));

                if("image".equals(post.get("mediaType")) && !mediaUrl.isEmpty()){
                    shareIntent.setType("image/*");
                    shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse(mediaUrl));
                }
                startActivity(Intent.createChooser(shareIntent, "Compartir post"));
            });

            // Gestión de likes en click
            holder.likeImageView.setOnClickListener(view -> {
                Databases databases = new Databases(client);
                List<String> nuevosLikes = new ArrayList<>(likes);
                if(nuevosLikes.contains(userId))
                    nuevosLikes.remove(userId);
                else
                    nuevosLikes.add(userId);

                Map<String, Object> data = new HashMap<>();
                data.put("likes", nuevosLikes);

                try {
                    databases.updateDocument(
                            getString(R.string.APPWRITE_DATABASE_ID),
                            getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                            postId,
                            data,
                            new ArrayList<>(),
                            new CoroutineCallback<>((result, error) -> {
                                if (error != null) {
                                    error.printStackTrace();
                                    return;
                                }
                                mainHandler.post(() -> obtenerPosts());
                            })
                    );
                } catch (AppwriteException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        @Override
        public int getItemCount() {
            return lista == null ? 0 : lista.getDocuments().size();
        }

        public void establecerLista(DocumentList<Map<String, Object>> lista) {
            this.lista = lista;
            notifyDataSetChanged();
        }
    }

    /**
     * Obtiene los posts de la base de datos.
     */
    void obtenerPosts() {
        Databases databases = new Databases(client);
        try {
            databases.listDocuments(
                    getString(R.string.APPWRITE_DATABASE_ID),
                    getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                    new ArrayList<>(),
                    new CoroutineCallback<>((result, error) -> {
                        if (error != null) {
                            Snackbar.make(requireView(), "Error al obtener los posts: "
                                    + error.toString(), Snackbar.LENGTH_LONG).show();
                            return;
                        }
                        mainHandler.post(() -> adapter.establecerLista(result));
                    })
            );
        } catch (AppwriteException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Elimina un post dado su ID.
     */
    private void eliminarPost(String postId) {
        Databases databases = new Databases(client);
        databases.deleteDocument(
                getString(R.string.APPWRITE_DATABASE_ID),
                getString(R.string.APPWRITE_POSTS_COLLECTION_ID),
                postId,
                new CoroutineCallback<>((result, error) -> {
                    if (error != null) {
                        Snackbar.make(requireView(), "Error al eliminar: " + error.getMessage(), Snackbar.LENGTH_LONG).show();
                        return;
                    }
                    mainHandler.post(this::obtenerPosts);
                })
        );
    }
}
