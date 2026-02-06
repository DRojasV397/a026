"""
Modulo de validacion de datos.
Valida estructura, tipos de datos y reglas de negocio.
"""

import pandas as pd
import numpy as np
from typing import Optional, List, Dict, Any, Callable, Tuple
from dataclasses import dataclass, field
from enum import Enum
from datetime import datetime, date
import re
import logging

logger = logging.getLogger(__name__)


class RuleType(str, Enum):
    """Tipos de reglas de validacion."""
    REQUIRED = "required"
    TYPE = "type"
    RANGE = "range"
    PATTERN = "pattern"
    UNIQUE = "unique"
    CUSTOM = "custom"
    REFERENTIAL = "referential"


class Severity(str, Enum):
    """Severidad de las violaciones."""
    ERROR = "error"
    WARNING = "warning"
    INFO = "info"


@dataclass
class ValidationRule:
    """Regla de validacion individual."""
    name: str
    rule_type: RuleType
    column: Optional[str] = None
    columns: Optional[List[str]] = None
    params: Dict[str, Any] = field(default_factory=dict)
    severity: Severity = Severity.ERROR
    message: Optional[str] = None
    custom_func: Optional[Callable] = None


@dataclass
class ValidationViolation:
    """Violacion de una regla de validacion."""
    rule_name: str
    rule_type: RuleType
    column: Optional[str]
    severity: Severity
    message: str
    affected_rows: int = 0
    sample_values: List[Any] = field(default_factory=list)
    row_indices: List[int] = field(default_factory=list)


@dataclass
class ValidationResult:
    """Resultado de la validacion."""
    is_valid: bool = True
    total_rows: int = 0
    valid_rows: int = 0
    invalid_rows: int = 0
    violations: List[ValidationViolation] = field(default_factory=list)
    errors: List[ValidationViolation] = field(default_factory=list)
    warnings: List[ValidationViolation] = field(default_factory=list)
    column_types: Dict[str, str] = field(default_factory=dict)
    summary: Dict[str, Any] = field(default_factory=dict)

    def to_dict(self) -> Dict[str, Any]:
        """Convierte el resultado a diccionario."""
        return {
            "is_valid": self.is_valid,
            "total_rows": self.total_rows,
            "valid_rows": self.valid_rows,
            "invalid_rows": self.invalid_rows,
            "error_count": len(self.errors),
            "warning_count": len(self.warnings),
            "violations": [
                {
                    "rule": v.rule_name,
                    "type": v.rule_type.value,
                    "column": v.column,
                    "severity": v.severity.value,
                    "message": v.message,
                    "affected_rows": v.affected_rows,
                    "sample_values": v.sample_values[:5]
                }
                for v in self.violations
            ],
            "column_types": self.column_types,
            "summary": self.summary
        }


class DataValidator:
    """
    Validador de datos con reglas configurables.

    Soporta validaciones de:
    - Columnas requeridas
    - Tipos de datos
    - Rangos de valores
    - Patrones (regex)
    - Unicidad
    - Reglas personalizadas
    """

    # Mapeo de tipos esperados a tipos pandas
    TYPE_MAPPING = {
        "string": ["object", "string"],
        "integer": ["int64", "int32", "Int64", "Int32"],
        "float": ["float64", "float32"],
        "numeric": ["int64", "int32", "float64", "float32", "Int64", "Int32"],
        "date": ["datetime64[ns]", "object"],
        "datetime": ["datetime64[ns]"],
        "boolean": ["bool", "boolean"]
    }

    def __init__(self, rules: Optional[List[ValidationRule]] = None):
        self.rules = rules or []
        self.result = ValidationResult()

    def add_rule(self, rule: ValidationRule) -> 'DataValidator':
        """Agrega una regla de validacion."""
        self.rules.append(rule)
        return self

    def add_required_columns(
        self,
        columns: List[str],
        severity: Severity = Severity.ERROR
    ) -> 'DataValidator':
        """Agrega reglas para columnas requeridas."""
        for col in columns:
            self.rules.append(ValidationRule(
                name=f"required_{col}",
                rule_type=RuleType.REQUIRED,
                column=col,
                severity=severity,
                message=f"Columna requerida '{col}' no encontrada"
            ))
        return self

    def add_type_rule(
        self,
        column: str,
        expected_type: str,
        severity: Severity = Severity.ERROR
    ) -> 'DataValidator':
        """Agrega regla de tipo de datos."""
        self.rules.append(ValidationRule(
            name=f"type_{column}",
            rule_type=RuleType.TYPE,
            column=column,
            params={"expected_type": expected_type},
            severity=severity,
            message=f"Columna '{column}' debe ser de tipo {expected_type}"
        ))
        return self

    def add_range_rule(
        self,
        column: str,
        min_val: Optional[float] = None,
        max_val: Optional[float] = None,
        severity: Severity = Severity.ERROR
    ) -> 'DataValidator':
        """Agrega regla de rango de valores."""
        self.rules.append(ValidationRule(
            name=f"range_{column}",
            rule_type=RuleType.RANGE,
            column=column,
            params={"min": min_val, "max": max_val},
            severity=severity,
            message=f"Valores de '{column}' fuera de rango [{min_val}, {max_val}]"
        ))
        return self

    def add_pattern_rule(
        self,
        column: str,
        pattern: str,
        severity: Severity = Severity.ERROR,
        message: Optional[str] = None
    ) -> 'DataValidator':
        """Agrega regla de patron regex."""
        self.rules.append(ValidationRule(
            name=f"pattern_{column}",
            rule_type=RuleType.PATTERN,
            column=column,
            params={"pattern": pattern},
            severity=severity,
            message=message or f"Valores de '{column}' no coinciden con patron"
        ))
        return self

    def add_unique_rule(
        self,
        column: str,
        severity: Severity = Severity.ERROR
    ) -> 'DataValidator':
        """Agrega regla de unicidad."""
        self.rules.append(ValidationRule(
            name=f"unique_{column}",
            rule_type=RuleType.UNIQUE,
            column=column,
            severity=severity,
            message=f"Columna '{column}' contiene valores duplicados"
        ))
        return self

    def add_custom_rule(
        self,
        name: str,
        func: Callable[[pd.DataFrame], Tuple[bool, List[int], str]],
        severity: Severity = Severity.ERROR
    ) -> 'DataValidator':
        """
        Agrega regla personalizada.

        Args:
            name: Nombre de la regla
            func: Funcion que recibe DataFrame y retorna (valid, row_indices, message)
            severity: Severidad de la violacion
        """
        self.rules.append(ValidationRule(
            name=name,
            rule_type=RuleType.CUSTOM,
            severity=severity,
            custom_func=func
        ))
        return self

    def validate(self, df: pd.DataFrame) -> ValidationResult:
        """
        Ejecuta todas las validaciones.

        Args:
            df: DataFrame a validar

        Returns:
            ValidationResult: Resultado de la validacion
        """
        self.result = ValidationResult()
        self.result.total_rows = len(df)

        # Detectar tipos de columnas
        self.result.column_types = {
            col: str(df[col].dtype) for col in df.columns
        }

        invalid_rows = set()

        for rule in self.rules:
            violation = self._validate_rule(df, rule)
            if violation:
                self.result.violations.append(violation)
                invalid_rows.update(violation.row_indices)

                if violation.severity == Severity.ERROR:
                    self.result.errors.append(violation)
                elif violation.severity == Severity.WARNING:
                    self.result.warnings.append(violation)

        self.result.invalid_rows = len(invalid_rows)
        self.result.valid_rows = self.result.total_rows - self.result.invalid_rows
        self.result.is_valid = len(self.result.errors) == 0

        # Generar resumen
        self.result.summary = {
            "columns_validated": len(df.columns),
            "rules_applied": len(self.rules),
            "rules_passed": len(self.rules) - len(self.result.violations),
            "rules_failed": len(self.result.violations),
            "validity_rate": (
                self.result.valid_rows / self.result.total_rows * 100
                if self.result.total_rows > 0 else 100
            )
        }

        logger.info(
            f"Validacion completada: {self.result.valid_rows}/{self.result.total_rows} "
            f"filas validas, {len(self.result.errors)} errores, {len(self.result.warnings)} warnings"
        )

        return self.result

    def _validate_rule(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida una regla individual."""
        try:
            if rule.rule_type == RuleType.REQUIRED:
                return self._validate_required(df, rule)
            elif rule.rule_type == RuleType.TYPE:
                return self._validate_type(df, rule)
            elif rule.rule_type == RuleType.RANGE:
                return self._validate_range(df, rule)
            elif rule.rule_type == RuleType.PATTERN:
                return self._validate_pattern(df, rule)
            elif rule.rule_type == RuleType.UNIQUE:
                return self._validate_unique(df, rule)
            elif rule.rule_type == RuleType.CUSTOM:
                return self._validate_custom(df, rule)
        except Exception as e:
            logger.error(f"Error validando regla {rule.name}: {str(e)}")
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=Severity.ERROR,
                message=f"Error al validar: {str(e)}"
            )

        return None

    def _validate_required(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida columna requerida."""
        if rule.column not in df.columns:
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=rule.severity,
                message=rule.message or f"Columna requerida '{rule.column}' no encontrada"
            )
        return None

    def _validate_type(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida tipo de datos de columna."""
        if rule.column not in df.columns:
            return None

        expected_type = rule.params.get("expected_type", "string")
        actual_type = str(df[rule.column].dtype)

        valid_types = self.TYPE_MAPPING.get(expected_type, [expected_type])

        if actual_type not in valid_types:
            # Intentar conversion
            if expected_type == "numeric":
                try:
                    pd.to_numeric(df[rule.column], errors='raise')
                    return None
                except (ValueError, TypeError):
                    pass
            elif expected_type in ["date", "datetime"]:
                try:
                    pd.to_datetime(df[rule.column], errors='raise')
                    return None
                except (ValueError, TypeError):
                    pass

            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=rule.severity,
                message=rule.message or f"Tipo incorrecto: esperado {expected_type}, encontrado {actual_type}",
                sample_values=df[rule.column].head(5).tolist()
            )

        return None

    def _validate_range(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida rango de valores."""
        if rule.column not in df.columns:
            return None

        col_data = df[rule.column]
        min_val = rule.params.get("min")
        max_val = rule.params.get("max")

        violations_mask = pd.Series([False] * len(df))

        if min_val is not None:
            violations_mask = violations_mask | (col_data < min_val)
        if max_val is not None:
            violations_mask = violations_mask | (col_data > max_val)

        if violations_mask.any():
            indices = df.index[violations_mask].tolist()
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=rule.severity,
                message=rule.message,
                affected_rows=int(violations_mask.sum()),
                sample_values=col_data[violations_mask].head(5).tolist(),
                row_indices=indices[:100]
            )

        return None

    def _validate_pattern(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida patron regex."""
        if rule.column not in df.columns:
            return None

        pattern = rule.params.get("pattern", ".*")
        col_data = df[rule.column].astype(str)

        matches = col_data.str.match(pattern, na=False)
        violations_mask = ~matches & col_data.notna()

        if violations_mask.any():
            indices = df.index[violations_mask].tolist()
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=rule.severity,
                message=rule.message,
                affected_rows=int(violations_mask.sum()),
                sample_values=col_data[violations_mask].head(5).tolist(),
                row_indices=indices[:100]
            )

        return None

    def _validate_unique(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida unicidad de valores."""
        if rule.column not in df.columns:
            return None

        duplicates = df[rule.column].duplicated(keep=False)

        if duplicates.any():
            dup_values = df.loc[duplicates, rule.column].unique()
            indices = df.index[duplicates].tolist()
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=rule.column,
                severity=rule.severity,
                message=rule.message,
                affected_rows=int(duplicates.sum()),
                sample_values=list(dup_values[:5]),
                row_indices=indices[:100]
            )

        return None

    def _validate_custom(
        self,
        df: pd.DataFrame,
        rule: ValidationRule
    ) -> Optional[ValidationViolation]:
        """Valida regla personalizada."""
        if rule.custom_func is None:
            return None

        valid, row_indices, message = rule.custom_func(df)

        if not valid:
            return ValidationViolation(
                rule_name=rule.name,
                rule_type=rule.rule_type,
                column=None,
                severity=rule.severity,
                message=message,
                affected_rows=len(row_indices),
                row_indices=row_indices[:100]
            )

        return None


# Validadores predefinidos para tipos de datos comunes
class CommonValidators:
    """Validadores predefinidos comunes."""

    @staticmethod
    def create_sales_validator() -> DataValidator:
        """Crea validador para datos de ventas."""
        return (
            DataValidator()
            .add_required_columns(["fecha", "total"])
            .add_type_rule("fecha", "date")
            .add_type_rule("total", "numeric")
            .add_range_rule("total", min_val=0)
        )

    @staticmethod
    def create_purchases_validator() -> DataValidator:
        """Crea validador para datos de compras."""
        return (
            DataValidator()
            .add_required_columns(["fecha", "total"])
            .add_type_rule("fecha", "date")
            .add_type_rule("total", "numeric")
            .add_range_rule("total", min_val=0)
        )

    @staticmethod
    def create_products_validator() -> DataValidator:
        """Crea validador para datos de productos."""
        return (
            DataValidator()
            .add_required_columns(["sku", "nombre", "precio"])
            .add_unique_rule("sku")
            .add_type_rule("precio", "numeric")
            .add_range_rule("precio", min_val=0)
        )
