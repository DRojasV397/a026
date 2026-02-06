"""
Pruebas unitarias para carga de archivos CSV y Excel.
Cubre file_parser.py y data_service.py.
"""

import pytest
import pandas as pd
import numpy as np
import os
import tempfile
from datetime import datetime, timedelta
from io import BytesIO


class TestFileParser:
    """Pruebas para el parser de archivos."""

    @pytest.fixture
    def sample_csv_content(self):
        """Contenido CSV de ejemplo."""
        return """fecha,producto,cantidad,precio,total
2024-01-01,Producto A,10,100.50,1005.00
2024-01-02,Producto B,5,200.00,1000.00
2024-01-03,Producto A,8,100.50,804.00
2024-01-04,Producto C,15,50.25,753.75
2024-01-05,Producto B,3,200.00,600.00"""

    @pytest.fixture
    def sample_csv_bytes(self, sample_csv_content):
        """Crea bytes de CSV."""
        return sample_csv_content.encode('utf-8')

    @pytest.fixture
    def sample_excel_bytes(self):
        """Crea bytes de archivo Excel."""
        df = pd.DataFrame({
            'fecha': pd.date_range('2024-01-01', periods=5),
            'producto': ['A', 'B', 'A', 'C', 'B'],
            'cantidad': [10, 5, 8, 15, 3],
            'precio': [100.50, 200.00, 100.50, 50.25, 200.00],
            'total': [1005.00, 1000.00, 804.00, 753.75, 600.00]
        })

        buffer = BytesIO()
        df.to_excel(buffer, index=False)
        return buffer.getvalue()

    def test_file_parser_import(self):
        """Test importacion del parser."""
        from app.utils.file_parser import FileParser
        assert FileParser is not None

    def test_file_type_enum(self):
        """Test enumeracion de tipos de archivo."""
        from app.utils.file_parser import FileType
        assert FileType.CSV.value == "csv"
        assert FileType.EXCEL.value == "xlsx"
        assert FileType.XLS.value == "xls"

    def test_parse_csv_file(self, sample_csv_bytes):
        """Test parsing de archivo CSV."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        assert result.success == True
        assert result.data is not None
        assert len(result.data) == 5
        assert 'fecha' in result.data.columns
        assert 'producto' in result.data.columns
        assert 'total' in result.data.columns

    def test_parse_excel_file(self, sample_excel_bytes):
        """Test parsing de archivo Excel."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_excel_bytes, "test.xlsx")

        assert result.success == True
        assert result.data is not None
        assert len(result.data) == 5
        assert 'fecha' in result.data.columns

    def test_detect_file_type_csv(self):
        """Test deteccion de tipo CSV."""
        from app.utils.file_parser import FileParser, FileType

        parser = FileParser()
        file_type = parser.detect_file_type("datos.csv")
        assert file_type == FileType.CSV

    def test_detect_file_type_excel(self):
        """Test deteccion de tipo Excel."""
        from app.utils.file_parser import FileParser, FileType

        parser = FileParser()
        file_type = parser.detect_file_type("datos.xlsx")
        assert file_type == FileType.EXCEL

    def test_detect_file_type_invalid(self):
        """Test deteccion de tipo invalido."""
        from app.utils.file_parser import FileParser
        from app.utils.exceptions import FileParseError

        parser = FileParser()
        with pytest.raises(FileParseError):
            parser.detect_file_type("datos.txt")

    def test_parse_result_to_dict(self, sample_csv_bytes):
        """Test conversion de ParseResult a diccionario."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        result_dict = result.to_dict()

        assert 'success' in result_dict
        assert 'total_rows' in result_dict
        assert 'column_info' in result_dict
        assert result_dict['success'] == True
        assert result_dict['total_rows'] == 5

    def test_validate_columns(self, sample_csv_bytes):
        """Test validacion de columnas."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        # Validar columnas existentes
        valid, missing, warnings = parser.validate_columns(
            result.data,
            required_columns=['fecha', 'total'],
            optional_columns=['descripcion']
        )

        assert valid == True
        assert len(missing) == 0
        assert len(warnings) == 1  # descripcion no encontrada

    def test_validate_columns_missing(self, sample_csv_bytes):
        """Test validacion de columnas faltantes."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        # Validar columnas faltantes
        valid, missing, warnings = parser.validate_columns(
            result.data,
            required_columns=['fecha', 'precio_unitario']  # precio_unitario no existe
        )

        assert valid == False
        assert 'precio_unitario' in missing

    def test_get_preview(self, sample_csv_bytes):
        """Test preview de datos."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        preview = parser.get_preview(result.data, rows=3)

        assert len(preview) == 3
        assert isinstance(preview, list)
        assert 'fecha' in preview[0]

    def test_column_info(self, sample_csv_bytes):
        """Test informacion de columnas."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        result = parser.parse_file(sample_csv_bytes, "test.csv")

        assert 'total' in result.column_info
        assert 'dtype' in result.column_info['total']
        assert 'null_count' in result.column_info['total']

    def test_parse_csv_with_encoding(self):
        """Test parsing con encoding especifico."""
        from app.utils.file_parser import FileParser

        # Contenido con caracteres especiales
        content = """fecha,producto,descripcion
2024-01-01,Cafe,Bebida caliente
2024-01-02,Te,Infusion"""

        parser = FileParser()
        result = parser.parse_file(content.encode('utf-8'), "test.csv", encoding='utf-8')

        assert result.success == True
        assert len(result.data) == 2

    def test_parse_empty_file(self):
        """Test archivo vacio."""
        from app.utils.file_parser import FileParser

        parser = FileParser()
        content = b"fecha,producto\n"  # Solo headers

        result = parser.parse_file(content, "test.csv")

        # Puede ser exitoso con 0 filas o fallar
        if result.success:
            assert result.total_rows == 0
        else:
            assert len(result.errors) > 0


class TestDataUploadSchemas:
    """Pruebas para schemas de carga de datos."""

    def test_data_type_enum(self):
        """Test enumeracion de tipos de datos."""
        from app.schemas.data_upload import DataType

        assert DataType.VENTAS.value == "ventas"
        assert DataType.COMPRAS.value == "compras"
        assert DataType.PRODUCTOS.value == "productos"

    def test_upload_status_enum(self):
        """Test enumeracion de estados de upload."""
        from app.schemas.data_upload import UploadStatus

        assert UploadStatus.PENDING.value == "pending"
        assert UploadStatus.READY.value == "ready"
        assert UploadStatus.ERROR.value == "error"

    def test_upload_response_schema(self):
        """Test schema de response de upload."""
        from app.schemas.data_upload import UploadResponse, UploadStatus

        response = UploadResponse(
            upload_id='abc123',
            filename='ventas_2024.csv',
            file_type='csv',
            total_rows=100,
            status=UploadStatus.READY,
            message='Archivo cargado exitosamente'
        )

        assert response.upload_id == 'abc123'
        assert response.total_rows == 100
        assert response.status == UploadStatus.READY

    def test_validate_response_schema(self):
        """Test schema de resultado de validacion."""
        from app.schemas.data_upload import ValidateResponse, DataType

        result = ValidateResponse(
            upload_id='abc123',
            valid=True,
            data_type=DataType.VENTAS,
            columns=[],
            warnings=['Algunas fechas estan en el futuro']
        )

        assert result.valid == True
        assert len(result.warnings) == 1

    def test_cleaning_options_schema(self):
        """Test schema de opciones de limpieza."""
        from app.schemas.data_upload import CleaningOptions

        options = CleaningOptions(
            remove_duplicates=True,
            handle_nulls=True,
            null_strategy='drop',
            detect_outliers=True,
            outlier_threshold=3.0
        )

        assert options.remove_duplicates == True
        assert options.null_strategy == 'drop'
        assert options.outlier_threshold == 3.0

    def test_quality_report_response_schema(self):
        """Test schema de reporte de calidad."""
        from app.schemas.data_upload import QualityReportResponse

        report = QualityReportResponse(
            upload_id='abc123',
            overall_score=95.0,
            total_rows=100,
            valid_rows=95,
            metrics=[]
        )

        assert report.total_rows == 100
        assert report.overall_score == 95.0

    def test_cleaning_result_schema(self):
        """Test schema de resultado de limpieza."""
        from app.schemas.data_upload import CleaningResult

        result = CleaningResult(
            original_rows=100,
            cleaned_rows=95,
            removed_rows=5,
            duplicates_removed=3,
            nulls_handled=2,
            outliers_detected=0,
            quality_score=95.0
        )

        assert result.original_rows == 100
        assert result.cleaned_rows == 95


class TestFileUploadIntegration:
    """Pruebas de integracion para carga de archivos."""

    @pytest.fixture
    def test_csv_bytes(self):
        """Genera bytes de CSV para pruebas."""
        content = """fecha,total,producto
2024-01-01,1000.00,A
2024-01-02,1500.00,B
2024-01-03,800.00,A
2024-01-04,2000.00,C
2024-01-05,1200.00,B"""
        return BytesIO(content.encode('utf-8'))

    def test_process_uploaded_file(self, test_csv_bytes):
        """Test procesamiento de archivo subido."""
        df = pd.read_csv(test_csv_bytes)

        assert len(df) == 5
        assert 'fecha' in df.columns
        assert 'total' in df.columns
        assert 'producto' in df.columns

    def test_validate_uploaded_data(self, test_csv_bytes):
        """Test validacion de datos subidos."""
        df = pd.read_csv(test_csv_bytes)

        # Validaciones basicas
        validations = {
            'has_required_columns': all(col in df.columns for col in ['fecha', 'total']),
            'has_data': len(df) > 0,
            'total_is_numeric': pd.api.types.is_numeric_dtype(df['total'])
        }

        assert all(validations.values())

    def test_transform_uploaded_data(self, test_csv_bytes):
        """Test transformacion de datos subidos."""
        df = pd.read_csv(test_csv_bytes)

        # Transformar fecha
        df['fecha'] = pd.to_datetime(df['fecha'])

        # Agregar columnas derivadas
        df['mes'] = df['fecha'].dt.month
        df['dia_semana'] = df['fecha'].dt.dayofweek

        assert 'mes' in df.columns
        assert 'dia_semana' in df.columns
        assert df['mes'].iloc[0] == 1  # Enero

    def test_full_pipeline(self):
        """Test pipeline completo de carga."""
        from app.utils.file_parser import FileParser

        # Crear datos de prueba
        content = """fecha,total,cantidad
2024-01-01,1000,10
2024-01-02,1500,15
2024-01-03,800,8"""

        parser = FileParser()
        result = parser.parse_file(content.encode('utf-8'), "test.csv")

        assert result.success == True

        # Validar columnas
        valid, missing, warnings = parser.validate_columns(
            result.data,
            required_columns=['fecha', 'total']
        )

        assert valid == True

        # Preview
        preview = parser.get_preview(result.data, rows=2)
        assert len(preview) == 2
