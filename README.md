# 📱 Aplicación de Reconocimiento Facial Biométrico

## 🎯 Descripción General

Aplicación Android desarrollada en Kotlin con Jetpack Compose que implementa reconocimiento facial biométrico utilizando Google ML Kit y almacenamiento local con Room (SQLite).

## 🔧 Tecnologías Utilizadas

- **Lenguaje:** Kotlin
- **UI:** Jetpack Compose (Material 3)
- **Cámara:** CameraX
- **Base de Datos:** Room (SQLite)
- **Reconocimiento Facial:** Google ML Kit Face Detection
- **Arquitectura:** MVVM (Model-View-ViewModel)
- **Concurrencia:** Kotlin Coroutines & Flow

## 📊 Arquitectura del Proyecto

```
app/
├── data/                          # Capa de datos
│   ├── FaceEntity.kt              # Entidad de base de datos
│   ├── FaceDao.kt                 # Acceso a datos SQL
│   ├── FaceDatabase.kt            # Configuración Room
│   └── FaceRepository.kt          # Repositorio (patrón Repository)
│
├── ml/                            # Machine Learning
│   └── FaceRecognitionProcessor.kt # Procesamiento facial con ML Kit
│
├── utils/                         # Utilidades
│   └── ImageUtils.kt              # Conversión de imágenes
│
├── viewmodel/                     # Lógica de negocio
│   └── FaceViewModel.kt           # ViewModel principal
│
├── ui/screens/                    # Pantallas Compose
│   ├── HomeScreen.kt              # Pantalla principal
│   ├── RegisterScreen.kt          # Registro de rostros
│   ├── RecognizeScreen.kt         # Reconocimiento
│   └── CameraScreen.kt            # Captura de cámara
│
├── navigation/                    # Navegación
│   ├── Screen.kt                  # Definición de rutas
│   └── AppNavigation.kt           # NavHost
│
└── MainActivity.kt                # Activity principal
```

## 🚀 Funcionalidades Principales

### 1. **Registro de Rostros**
- Captura imagen facial con cámara frontal
- Validación de rostro único en la imagen
- **Validación de DNI único** (no permite duplicados)
- Extracción de características faciales (embedding)
- Almacenamiento en base de datos SQLite local

### 2. **Reconocimiento Facial**
- Captura imagen para reconocimiento
- Detección de rostro con ML Kit
- Comparación con rostros registrados usando similitud coseno
- **Umbral de confianza: 60% mínimo** para aceptar coincidencia
- Muestra nombre, DNI y porcentaje de confianza

### 3. **Almacenamiento Seguro**
- Base de datos Room (SQLite) local
- Imágenes comprimidas en formato JPEG (calidad 80%)
- Vectores de características (embeddings) serializados

## 📐 Cómo Funciona el Reconocimiento

### **Umbral de Confianza**

La aplicación usa un **umbral del 60%** para determinar si un rostro es reconocido:

```kotlin
companion object {
    const val RECOGNITION_THRESHOLD = 0.6f // 60% de confianza mínima
}
```

**¿Qué significa esto?**
- **60% o menos:** Rostro NO reconocido (similitud insuficiente)
- **61-75%:** Reconocido con confianza moderada
- **76-89%:** Reconocido con buena confianza
- **90-100%:** Reconocido con alta confianza

### **Proceso de Reconocimiento**

1. **Captura de Imagen**
   - Usuario captura foto con cámara frontal
   - Imagen se rota automáticamente a orientación correcta

2. **Detección de Rostro** (ML Kit)
   ```
   - Detecta si hay un rostro en la imagen
   - Valida que sea solo 1 rostro
   - Identifica landmarks: ojos, nariz, boca, etc.
   ```

3. **Extracción de Características (Embedding)**
   ```
   Vector de características extraídas:
   - Dimensiones del rostro (ancho, alto, posición)
   - Posiciones de landmarks faciales (ojos, nariz, boca)
   - Ángulos de rotación de cabeza (X, Y, Z)
   - Expresiones (sonrisa, ojos abiertos)
   
   Resultado: Vector normalizado de ~50 dimensiones
   ```

4. **Comparación con Base de Datos**
   ```kotlin
   Para cada rostro registrado:
     1. Calcular similitud coseno entre embeddings
     2. Similitud = cos(θ) entre dos vectores
     3. Rango: 0.0 (diferente) a 1.0 (idéntico)
   ```

5. **Similitud Coseno** (Método Matemático)
   ```
   Fórmula: similitud = (Vector_A · Vector_B) / (|A| × |B|)
   
   Como los vectores están normalizados (|A| = |B| = 1):
   similitud = Vector_A · Vector_B (producto punto)
   
   Ejemplo:
   - Misma persona: 0.85 (85% similar) ✓ RECONOCIDO
   - Persona diferente: 0.35 (35% similar) ✗ NO RECONOCIDO
   ```

6. **Decisión Final**
   - Si similitud ≥ 60%: **RECONOCIDO** ✓
   - Si similitud < 60%: **NO RECONOCIDO** ✗

## 🔐 Validaciones Implementadas

### **Registro de Rostros**
✅ Un solo rostro en la imagen (no múltiples)
✅ DNI único (no permite duplicados)
✅ DNI y nombre no vacíos
✅ Rostro detectado correctamente

### **Reconocimiento**
✅ Un solo rostro en la imagen
✅ Rostro detectado correctamente
✅ Al menos 1 rostro registrado en BD
✅ Similitud mínima del 60%

## 📦 Dependencias Principales

```kotlin
// CameraX - Captura de imágenes
implementation("androidx.camera:camera-camera2:1.3.1")
implementation("androidx.camera:camera-lifecycle:1.3.1")
implementation("androidx.camera:camera-view:1.3.1")

// Room - Base de datos SQLite
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
ksp("androidx.room:room-compiler:2.6.1")

// ML Kit - Detección facial
implementation("com.google.mlkit:face-detection:16.1.6")

// Coroutines para ML Kit
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.7.3")

// Navigation Compose
implementation("androidx.navigation:navigation-compose:2.7.6")

// Permisos
implementation("com.google.accompanist:accompanist-permissions:0.32.0")
```

## 🎨 Flujo de Usuario

### **Pantalla Principal (HomeScreen)**
```
┌─────────────────────────┐
│ Reconocimiento Facial   │
├─────────────────────────┤
│                         │
│  [Registrar Rostro]     │
│  [Reconocer Rostro]     │
│                         │
└─────────────────────────┘
```

### **Flujo de Registro**
```
1. Pantalla Registro
   ↓
2. Ingresar DNI y Nombre
   ↓
3. Capturar Foto (Cámara)
   ↓
4. Validar DNI único ← NUEVA VALIDACIÓN
   ↓
5. Procesar rostro (ML Kit)
   ↓
6. Guardar en BD (Room)
   ↓
7. Confirmación: "✓ Rostro registrado exitosamente"
```

### **Flujo de Reconocimiento**
```
1. Pantalla Reconocimiento
   ↓
2. Capturar Foto (Cámara)
   ↓
3. Detectar rostro (ML Kit)
   ↓
4. Extraer embedding
   ↓
5. Comparar con todos los rostros en BD
   ↓
6. Calcular similitud coseno
   ↓
7. ¿Similitud ≥ 60%?
   │
   ├─ SÍ → "✓ Rostro reconocido"
   │        Nombre: Juan Pérez
   │        DNI: 12345678
   │        Confianza: 85%
   │
   └─ NO → "✗ Rostro no reconocido"
            Similitud inferior al 60%
```

## 🔬 Detalles Técnicos del Embedding

### **Características Extraídas (Embedding)**

```kotlin
1. GEOMETRÍA DEL ROSTRO (4 valores)
   - Ancho del bounding box
   - Alto del bounding box
   - Centro X
   - Centro Y

2. LANDMARKS FACIALES (~20-30 valores)
   Cada landmark tiene X, Y:
   - Ojo izquierdo (interno, externo)
   - Ojo derecho (interno, externo)
   - Nariz (base, punta)
   - Boca (izquierda, derecha, superior, inferior)
   - Orejas
   - Mejillas

3. ÁNGULOS EULER (3 valores)
   - headEulerAngleX: inclinación vertical
   - headEulerAngleY: rotación horizontal
   - headEulerAngleZ: inclinación lateral

4. EXPRESIONES (3 valores)
   - Probabilidad de sonrisa
   - Ojo izquierdo abierto
   - Ojo derecho abierto

Total: ~40-50 dimensiones por rostro
```

## 🚨 Manejo de Errores

La aplicación maneja diversos casos de error:

- ❌ No se detectó ningún rostro
- ❌ Se detectaron múltiples rostros
- ❌ DNI duplicado (ya registrado)
- ❌ Campos vacíos (DNI o nombre)
- ❌ No hay rostros en la base de datos
- ❌ Similitud insuficiente (< 60%)
- ❌ Error de cámara o permisos

## 📱 Permisos Requeridos

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

## 🎓 Notas para Mejora Futura

### **Embedding Actual (Geométrico)**
- Usa características geométricas y landmarks
- Funcional para escenarios básicos
- Sensible a cambios de iluminación y expresiones

### **Mejora Recomendada (Deep Learning)**
Para mayor precisión en producción:
- **FaceNet:** Embedding de 128/512 dimensiones
- **MobileFaceNet:** Optimizado para móviles
- **ArcFace:** Estado del arte en reconocimiento facial
- Usar TensorFlow Lite con modelos pre-entrenados

## 📄 Licencia

Proyecto educativo de demostración.

## 👨‍💻 Autor

Desarrollado como ejemplo de aplicación de reconocimiento facial biométrico en Android.

