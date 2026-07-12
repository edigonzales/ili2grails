# Generated from ili2grails Core-IR.
from django.db import models

class AssociationWithAttribute(models.Model):
    document_role = models.ForeignKey("Document", on_delete=models.PROTECT, db_column="document_role_id", null=True, blank=True, related_name="+")
    person_role = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="person_role_id", null=True, blank=True, related_name="+")
    role_note = models.CharField(max_length=30, null=True, blank=True)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "associationwithattribute"
        managed = False


class Building(models.Model):
    name = models.CharField(max_length=40, null=True, blank=True)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "building"
        managed = False


class Document(models.Model):
    title = models.CharField(max_length=80)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "document"
        managed = False


class EmptyAssociation(models.Model):
    parcel_role = models.ForeignKey("Parcel", on_delete=models.PROTECT, db_column="parcel_role_id", null=True, blank=True, related_name="+")
    person_role = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="person_role_id", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "emptyassociation"
        managed = False


class ExternalCompositeAssociation(models.Model):
    buildings = models.ForeignKey("Building", on_delete=models.PROTECT, db_column="building_id", null=True, blank=True, related_name="+")
    owner = models.ForeignKey("Person", on_delete=models.CASCADE, db_column="owner_id", related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "externalcompositeassociation"
        managed = False


class OrderedAssociation(models.Model):
    docs = models.ForeignKey("Document", on_delete=models.PROTECT, db_column="docs_id", null=True, blank=True, related_name="+")
    owner = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="owner_id", related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "orderedassociation"
        managed = False


class Parcel(models.Model):
    ident = models.CharField(max_length=20)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "parcel"
        managed = False


class Person(models.Model):
    name = models.CharField(max_length=50)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "person"
        managed = False


class PhysicalMismatchAssociation(models.Model):
    semantic_owner = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="owner_fk", related_name="+")
    owned_parcel = models.ForeignKey("Parcel", on_delete=models.PROTECT, db_column="parcel_fk", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "physicalmismatchassociation"
        managed = False


class SameTargetAssociation(models.Model):
    primary_person = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="primary_person_id", null=True, blank=True, related_name="+")
    secondary_person = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="secondary_person_id", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "sametargetassociation"
        managed = False


class ExtendedParcel(models.Model):
    extra_code = models.CharField(max_length=20, null=True, blank=True)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "extendedparcel"
        managed = False


class ExtendedTopicAssociation(models.Model):
    extended_parcel_role = models.ForeignKey("ExtendedParcel", on_delete=models.PROTECT, db_column="ext_parcel_id", null=True, blank=True, related_name="+")
    extended_person_role = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="ext_person_id", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "extendedtopicassociation"
        managed = False


class TernaryAssociation(models.Model):
    document_role = models.ForeignKey("Document", on_delete=models.PROTECT, db_column="document_role_id", null=True, blank=True, related_name="+")
    note = models.CharField(max_length=50, null=True, blank=True)
    parcel_role = models.ForeignKey("Parcel", on_delete=models.PROTECT, db_column="parcel_role_id", null=True, blank=True, related_name="+")
    person_role = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="person_role_id", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "ternaryassociation"
        managed = False


