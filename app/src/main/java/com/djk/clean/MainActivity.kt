package com.djk.clean

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : AppCompatActivity() {

    // ------------------------------------------------------------------
    // Constantes (équivalent des constantes du script Python d'origine)
    // ------------------------------------------------------------------
    private val motSecret = "etienne"
    private val dossiersMedias = listOf("DCIM", "Pictures", "Movies", "WhatsApp/Media")
    private val nomCorbeille = ".djk_corbeille"
    private val extensionsMedia = listOf(
        "jpg", "jpeg", "png", "gif", "webp", "heic",
        "mp4", "mov", "mkv", "3gp", "avi"
    )

    private lateinit var barreRecherche: EditText
    private lateinit var boutonRestaurer: Button
    private lateinit var statutLabel: TextView

    private val scope = CoroutineScope(Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        barreRecherche = findViewById(R.id.barre_recherche)
        boutonRestaurer = findViewById(R.id.bouton_restaurer)
        statutLabel = findViewById(R.id.statut_label)
        val boutonNettoyer: Button = findViewById(R.id.bouton_nettoyer)

        barreRecherche.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                boutonRestaurer.visibility =
                    if (s.toString().trim().lowercase() == motSecret) View.VISIBLE else View.GONE
            }
        })

        boutonNettoyer.setOnClickListener {
            demanderPermissions()
            lancerNettoyage()
        }

        boutonRestaurer.setOnClickListener {
            lancerRestauration()
        }
    }

    // ------------------------------------------------------------------
    // Permissions
    // ------------------------------------------------------------------
    private fun demanderPermissions() {
        val permissions = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            permissions.add(Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        val aDemander = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (aDemander.isNotEmpty()) {
            permissionLauncher.launch(aDemander.toTypedArray())
        }

        // Android 11+ (API 30+) : accès complet au stockage nécessaire pour déplacer
        // des fichiers en dehors des dossiers standards de l'app. Ne peut pas être
        // demandé comme une permission classique -> redirection vers les réglages.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
                statutLabel.text = "Autorise l'accès complet au stockage, puis reviens et réessaie."
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
            }
        }
    }

    private fun permissionsOk(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return ContextCompat.checkSelfPermission(
            this, Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }

    // ------------------------------------------------------------------
    // Chemins
    // ------------------------------------------------------------------
    private fun cheminStockage(): File = Environment.getExternalStorageDirectory()

    private fun cheminCorbeille(): File = File(cheminStockage(), nomCorbeille)

    // ------------------------------------------------------------------
    // Listage des médias
    // ------------------------------------------------------------------
    private fun listerMedias(): List<File> {
        val trouves = mutableListOf<File>()
        val base = cheminStockage()
        for (dossier in dossiersMedias) {
            val dossierMedia = File(base, dossier)
            if (!dossierMedia.isDirectory) continue
            dossierMedia.walkTopDown().forEach { f ->
                if (f.isFile &&
                    !f.path.contains(nomCorbeille) &&
                    extensionsMedia.any { f.name.lowercase().endsWith(".$it") }
                ) {
                    trouves.add(f)
                }
            }
        }
        return trouves
    }

    // ------------------------------------------------------------------
    // Rescan MediaStore (pour que la galerie "oublie" ou "redécouvre" un fichier)
    // ------------------------------------------------------------------
    private fun rescannerFichier(chemin: String) {
        MediaScannerConnection.scanFile(this, arrayOf(chemin), null, null)
    }

    // ------------------------------------------------------------------
    // Nettoyage
    // ------------------------------------------------------------------
    private fun lancerNettoyage() {
        if (!permissionsOk()) {
            statutLabel.text = "Permissions manquantes. Autorise l'accès au stockage."
            return
        }
        statutLabel.text = "Nettoyage en cours..."

        scope.launch {
            val (deplaces, total) = withContext(Dispatchers.IO) { nettoyerGalerie() }
            statutLabel.text = "Terminé — $deplaces/$total médias cachés."
        }
    }

    private fun nettoyerGalerie(): Pair<Int, Int> {
        val corbeille = cheminCorbeille()
        corbeille.mkdirs()

        val nomedia = File(corbeille, ".nomedia")
        if (!nomedia.exists()) nomedia.createNewFile()

        val medias = listerMedias()
        val total = medias.size
        var deplaces = 0

        val manifestFile = File(corbeille, "_manifest.txt")
        val manifest = StringBuilder()
        val horodatage = System.currentTimeMillis()

        medias.forEachIndexed { i, source ->
            val destination = File(corbeille, "${horodatage}_${i}__${source.name}")
            try {
                val cheminOriginal = source.absolutePath
                if (source.renameTo(destination)) {
                    manifest.append("${destination.absolutePath}|$cheminOriginal\n")
                    rescannerFichier(cheminOriginal)
                    deplaces++
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        manifestFile.appendText(manifest.toString())
        return Pair(deplaces, total)
    }

    // ------------------------------------------------------------------
    // Restauration
    // ------------------------------------------------------------------
    private fun lancerRestauration() {
        if (!permissionsOk()) {
            statutLabel.text = "Permissions manquantes. Autorise l'accès au stockage."
            return
        }
        statutLabel.text = "Restauration en cours..."

        scope.launch {
            val (restaures, total) = withContext(Dispatchers.IO) { restaurerGalerie() }
            statutLabel.text = "Terminé — $restaures/$total médias restaurés."
            barreRecherche.setText("")
        }
    }

    private fun restaurerGalerie(): Pair<Int, Int> {
        val corbeille = cheminCorbeille()
        val manifestFile = File(corbeille, "_manifest.txt")
        if (!manifestFile.exists()) return Pair(0, 0)

        val lignes = manifestFile.readLines().map { it.trim() }.filter { it.isNotEmpty() }
        val total = lignes.size
        var restaures = 0
        val restantes = mutableListOf<String>()

        for (ligne in lignes) {
            try {
                val parts = ligne.split("|")
                val source = File(parts[0])
                val destinationOriginale = File(parts[1])
                destinationOriginale.parentFile?.mkdirs()
                if (source.renameTo(destinationOriginale)) {
                    rescannerFichier(destinationOriginale.absolutePath)
                    restaures++
                } else {
                    restantes.add(ligne)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                restantes.add(ligne)
            }
        }

        manifestFile.writeText(if (restantes.isNotEmpty()) restantes.joinToString("\n") + "\n" else "")
        return Pair(restaures, total)
    }
}
