"""
Modelo K-Means para segmentacion de productos.
RF-02.04: Implementar modelo de clustering para segmentacion.
RN-03.06: Minimo 10 productos para realizar clustering.
"""

import pandas as pd
import numpy as np
from typing import Optional, Dict, Any, List, Union, Tuple
from datetime import datetime
from dataclasses import dataclass, field
import logging

logger = logging.getLogger(__name__)


@dataclass
class ClusteringConfig:
    """Configuracion para modelo de clustering."""
    n_clusters: int = 3
    max_iter: int = 300
    n_init: int = 10
    init: str = "k-means++"  # k-means++, random
    algorithm: str = "lloyd"  # lloyd, elkan
    random_state: int = 42
    min_samples: int = 10  # RN-03.06: Minimo 10 productos


@dataclass
class ClusterInfo:
    """Informacion de un cluster."""
    cluster_id: int
    size: int
    percentage: float
    centroid: Dict[str, float]
    characteristics: Dict[str, Any]
    members: List[Any] = field(default_factory=list)

    def to_dict(self) -> Dict[str, Any]:
        return {
            "cluster_id": self.cluster_id,
            "size": self.size,
            "percentage": round(self.percentage, 2),
            "centroid": {k: round(v, 4) for k, v in self.centroid.items()},
            "characteristics": self.characteristics,
            "members_count": len(self.members)
        }


@dataclass
class ClusteringResult:
    """Resultado del clustering."""
    n_clusters: int
    inertia: float
    silhouette_score: float
    calinski_harabasz_score: float
    davies_bouldin_score: float
    clusters: List[ClusterInfo]
    labels: List[int]
    feature_names: List[str]

    def to_dict(self) -> Dict[str, Any]:
        return {
            "n_clusters": self.n_clusters,
            "metrics": {
                "inertia": round(self.inertia, 4),
                "silhouette_score": round(self.silhouette_score, 4),
                "calinski_harabasz_score": round(self.calinski_harabasz_score, 4),
                "davies_bouldin_score": round(self.davies_bouldin_score, 4)
            },
            "clusters": [c.to_dict() for c in self.clusters],
            "total_samples": len(self.labels)
        }


class KMeansClustering:
    """
    Modelo K-Means para segmentacion de productos.

    Usos:
    - Segmentar productos por comportamiento de ventas
    - Identificar grupos de clientes similares
    - Clasificar productos por rentabilidad

    RN-03.06: Requiere minimo 10 productos para clustering.
    """

    # RN-03.06: Minimo de muestras requeridas
    MIN_SAMPLES = 10

    def __init__(self, config: Optional[ClusteringConfig] = None):
        self.config = config or ClusteringConfig()
        self.model = None
        self.scaler = None
        self.is_fitted = False
        self.labels_: Optional[np.ndarray] = None
        self.cluster_centers_: Optional[np.ndarray] = None
        self.feature_names: List[str] = []
        self.original_data: Optional[pd.DataFrame] = None
        self._clustering_result: Optional[ClusteringResult] = None

    def validate_data(self, X: Union[pd.DataFrame, np.ndarray]) -> Tuple[bool, List[str]]:
        """
        Valida que los datos cumplan los requisitos.

        RN-03.06: Minimo 10 productos para clustering.

        Returns:
            Tuple[bool, List[str]]: (es_valido, lista_de_problemas)
        """
        issues = []

        n_samples = X.shape[0] if hasattr(X, 'shape') else len(X)

        if n_samples < self.MIN_SAMPLES:
            issues.append(
                f"Datos insuficientes: {n_samples} muestras. "
                f"Minimo requerido: {self.MIN_SAMPLES} (RN-03.06)"
            )

        if hasattr(X, 'isnull'):
            null_count = X.isnull().sum().sum()
            if null_count > 0:
                issues.append(f"Hay {null_count} valores nulos en los datos")

        return len(issues) == 0, issues

    def fit(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        scale: bool = True
    ) -> ClusteringResult:
        """
        Ajusta el modelo K-Means a los datos.

        Args:
            X: Datos de entrada (features)
            scale: Si normalizar los datos antes del clustering

        Returns:
            ClusteringResult con resultados del clustering
        """
        try:
            from sklearn.cluster import KMeans
            from sklearn.preprocessing import StandardScaler
            from sklearn.metrics import (
                silhouette_score, calinski_harabasz_score, davies_bouldin_score
            )
        except ImportError:
            raise ImportError(
                "Se requiere scikit-learn. Instalar con: pip install scikit-learn"
            )

        # Validar datos
        valid, issues = self.validate_data(X)
        if not valid:
            raise ValueError(f"Datos no validos: {'; '.join(issues)}")

        # Guardar datos originales
        if isinstance(X, pd.DataFrame):
            self.original_data = X.copy()
            self.feature_names = list(X.columns)
            X_array = X.values
        else:
            X_array = np.array(X)
            self.feature_names = [f"feature_{i}" for i in range(X_array.shape[1])]

        # Escalar datos si se requiere
        if scale:
            self.scaler = StandardScaler()
            X_scaled = self.scaler.fit_transform(X_array)
        else:
            X_scaled = X_array

        # Crear y ajustar modelo
        self.model = KMeans(
            n_clusters=self.config.n_clusters,
            max_iter=self.config.max_iter,
            n_init=self.config.n_init,
            init=self.config.init,
            algorithm=self.config.algorithm,
            random_state=self.config.random_state
        )

        self.labels_ = self.model.fit_predict(X_scaled)
        self.cluster_centers_ = self.model.cluster_centers_
        self.is_fitted = True

        # Calcular metricas
        if len(np.unique(self.labels_)) > 1:
            silhouette = float(silhouette_score(X_scaled, self.labels_))
            calinski = float(calinski_harabasz_score(X_scaled, self.labels_))
            davies = float(davies_bouldin_score(X_scaled, self.labels_))
        else:
            silhouette = 0.0
            calinski = 0.0
            davies = 0.0

        # Generar informacion de clusters
        clusters_info = self._generate_cluster_info(X_array)

        self._clustering_result = ClusteringResult(
            n_clusters=self.config.n_clusters,
            inertia=float(self.model.inertia_),
            silhouette_score=silhouette,
            calinski_harabasz_score=calinski,
            davies_bouldin_score=davies,
            clusters=clusters_info,
            labels=list(self.labels_),
            feature_names=self.feature_names
        )

        logger.info(
            f"K-Means ajustado. Clusters: {self.config.n_clusters}, "
            f"Silhouette: {silhouette:.4f}, Inertia: {self.model.inertia_:.4f}"
        )

        return self._clustering_result

    def _generate_cluster_info(
        self,
        X: np.ndarray
    ) -> List[ClusterInfo]:
        """Genera informacion detallada de cada cluster."""
        clusters_info = []
        total_samples = len(self.labels_)

        for cluster_id in range(self.config.n_clusters):
            mask = self.labels_ == cluster_id
            cluster_data = X[mask]
            cluster_size = len(cluster_data)

            # Centroide en escala original
            if self.scaler is not None:
                centroid_scaled = self.cluster_centers_[cluster_id]
                centroid = self.scaler.inverse_transform([centroid_scaled])[0]
            else:
                centroid = self.cluster_centers_[cluster_id]

            centroid_dict = {
                name: float(val)
                for name, val in zip(self.feature_names, centroid)
            }

            # Caracteristicas del cluster
            characteristics = {
                "mean": {
                    name: float(np.mean(cluster_data[:, i]))
                    for i, name in enumerate(self.feature_names)
                },
                "std": {
                    name: float(np.std(cluster_data[:, i]))
                    for i, name in enumerate(self.feature_names)
                },
                "min": {
                    name: float(np.min(cluster_data[:, i]))
                    for i, name in enumerate(self.feature_names)
                },
                "max": {
                    name: float(np.max(cluster_data[:, i]))
                    for i, name in enumerate(self.feature_names)
                }
            }

            # Indices de los miembros
            members = list(np.where(mask)[0])

            clusters_info.append(ClusterInfo(
                cluster_id=cluster_id,
                size=cluster_size,
                percentage=(cluster_size / total_samples) * 100,
                centroid=centroid_dict,
                characteristics=characteristics,
                members=members
            ))

        return clusters_info

    def predict(
        self,
        X: Union[pd.DataFrame, np.ndarray]
    ) -> np.ndarray:
        """
        Predice el cluster para nuevas muestras.

        Args:
            X: Nuevos datos

        Returns:
            Array con etiquetas de cluster
        """
        if not self.is_fitted:
            raise ValueError("El modelo debe ser ajustado primero (fit)")

        if isinstance(X, pd.DataFrame):
            X = X.values

        if self.scaler is not None:
            X = self.scaler.transform(X)

        return self.model.predict(X)

    def find_optimal_clusters(
        self,
        X: Union[pd.DataFrame, np.ndarray],
        min_clusters: int = 2,
        max_clusters: int = 10,
        method: str = "silhouette"
    ) -> Dict[str, Any]:
        """
        Encuentra el numero optimo de clusters.

        Args:
            X: Datos de entrada
            min_clusters: Minimo de clusters a probar
            max_clusters: Maximo de clusters a probar
            method: Metodo de evaluacion (silhouette, elbow, both)

        Returns:
            Dict con resultados del analisis
        """
        try:
            from sklearn.cluster import KMeans
            from sklearn.preprocessing import StandardScaler
            from sklearn.metrics import silhouette_score
        except ImportError:
            raise ImportError("Se requiere scikit-learn")

        if isinstance(X, pd.DataFrame):
            X = X.values

        # Escalar datos
        scaler = StandardScaler()
        X_scaled = scaler.fit_transform(X)

        results = {
            "k_values": [],
            "inertias": [],
            "silhouette_scores": []
        }

        for k in range(min_clusters, max_clusters + 1):
            kmeans = KMeans(
                n_clusters=k,
                n_init=self.config.n_init,
                random_state=self.config.random_state
            )
            labels = kmeans.fit_predict(X_scaled)

            results["k_values"].append(k)
            results["inertias"].append(float(kmeans.inertia_))

            if k > 1:
                sil_score = float(silhouette_score(X_scaled, labels))
            else:
                sil_score = 0.0
            results["silhouette_scores"].append(sil_score)

        # Encontrar optimo
        if method in ["silhouette", "both"]:
            optimal_silhouette = results["k_values"][
                np.argmax(results["silhouette_scores"])
            ]
        else:
            optimal_silhouette = None

        # Metodo del codo (buscar el "codo" en la curva de inercia)
        if method in ["elbow", "both"]:
            inertias = np.array(results["inertias"])
            # Calcular la segunda derivada para encontrar el codo
            diffs = np.diff(inertias)
            diffs2 = np.diff(diffs)
            optimal_elbow = results["k_values"][np.argmax(diffs2) + 2]
        else:
            optimal_elbow = None

        return {
            "analysis": results,
            "optimal_k_silhouette": optimal_silhouette,
            "optimal_k_elbow": optimal_elbow,
            "recommendation": optimal_silhouette or optimal_elbow
        }

    def get_cluster_for_item(
        self,
        item_index: int
    ) -> Optional[ClusterInfo]:
        """Obtiene la informacion del cluster de un item."""
        if not self.is_fitted or self.labels_ is None:
            return None

        if item_index >= len(self.labels_):
            return None

        cluster_id = self.labels_[item_index]
        return self._clustering_result.clusters[cluster_id]

    def get_similar_items(
        self,
        item_index: int,
        n: int = 5
    ) -> List[int]:
        """
        Encuentra los items mas similares dentro del mismo cluster.

        Args:
            item_index: Indice del item
            n: Numero de items similares a retornar

        Returns:
            Lista de indices de items similares
        """
        if not self.is_fitted or self.original_data is None:
            return []

        cluster_id = self.labels_[item_index]
        cluster_mask = self.labels_ == cluster_id
        cluster_indices = np.where(cluster_mask)[0]

        if len(cluster_indices) <= 1:
            return []

        # Calcular distancia al item
        item_data = self.original_data.iloc[item_index].values
        distances = []

        for idx in cluster_indices:
            if idx != item_index:
                other_data = self.original_data.iloc[idx].values
                dist = np.linalg.norm(item_data - other_data)
                distances.append((idx, dist))

        # Ordenar por distancia
        distances.sort(key=lambda x: x[1])

        return [idx for idx, _ in distances[:n]]

    def segment_products(
        self,
        products_df: pd.DataFrame,
        feature_columns: List[str],
        product_id_column: str = "idProducto",
        product_name_column: Optional[str] = "nombre"
    ) -> Dict[str, Any]:
        """
        Segmenta productos y retorna resultados detallados.

        Args:
            products_df: DataFrame con datos de productos
            feature_columns: Columnas a usar como features
            product_id_column: Columna con ID de producto
            product_name_column: Columna con nombre de producto

        Returns:
            Dict con segmentacion de productos
        """
        # Validar minimo de productos (RN-03.06)
        if len(products_df) < self.MIN_SAMPLES:
            return {
                "success": False,
                "error": f"Se requieren minimo {self.MIN_SAMPLES} productos (RN-03.06). "
                         f"Actualmente hay {len(products_df)}."
            }

        # Preparar datos
        X = products_df[feature_columns].copy()

        # Ajustar modelo
        result = self.fit(X)

        # Agregar etiquetas al DataFrame
        products_df = products_df.copy()
        products_df['cluster'] = self.labels_

        # Generar segmentacion por cluster
        segmentation = []
        for cluster_info in result.clusters:
            cluster_products = products_df[
                products_df['cluster'] == cluster_info.cluster_id
            ]

            products_list = []
            for _, row in cluster_products.iterrows():
                product_data = {
                    "id": row[product_id_column]
                }
                if product_name_column and product_name_column in row:
                    product_data["nombre"] = row[product_name_column]
                products_list.append(product_data)

            segmentation.append({
                "cluster_id": cluster_info.cluster_id,
                "cluster_name": self._generate_cluster_name(cluster_info),
                "size": cluster_info.size,
                "percentage": round(cluster_info.percentage, 2),
                "centroid": cluster_info.centroid,
                "products": products_list
            })

        return {
            "success": True,
            "n_clusters": result.n_clusters,
            "metrics": {
                "silhouette_score": round(result.silhouette_score, 4),
                "inertia": round(result.inertia, 4)
            },
            "segmentation": segmentation,
            "feature_columns": feature_columns
        }

    def _generate_cluster_name(self, cluster_info: ClusterInfo) -> str:
        """Genera un nombre descriptivo para el cluster basado en sus caracteristicas."""
        # Analizar centroide para generar nombre
        centroid = cluster_info.centroid

        if not centroid:
            return f"Cluster {cluster_info.cluster_id}"

        # Encontrar la caracteristica mas distintiva
        max_feature = max(centroid.items(), key=lambda x: x[1])
        min_feature = min(centroid.items(), key=lambda x: x[1])

        # Generar nombre basado en el tamaño
        if cluster_info.percentage > 40:
            size_desc = "Principal"
        elif cluster_info.percentage > 20:
            size_desc = "Mediano"
        else:
            size_desc = "Nicho"

        return f"Segmento {cluster_info.cluster_id + 1} ({size_desc})"

    def get_summary(self) -> Dict[str, Any]:
        """Retorna resumen del clustering."""
        if not self.is_fitted or self._clustering_result is None:
            return {"status": "Modelo no ajustado"}

        return {
            "status": "Ajustado",
            "n_clusters": self.config.n_clusters,
            "n_samples": len(self.labels_),
            "features": self.feature_names,
            "metrics": {
                "inertia": round(self._clustering_result.inertia, 4),
                "silhouette_score": round(self._clustering_result.silhouette_score, 4),
                "calinski_harabasz_score": round(
                    self._clustering_result.calinski_harabasz_score, 4
                ),
                "davies_bouldin_score": round(
                    self._clustering_result.davies_bouldin_score, 4
                )
            },
            "cluster_sizes": {
                f"cluster_{c.cluster_id}": c.size
                for c in self._clustering_result.clusters
            }
        }
