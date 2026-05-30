# Generated from ili2grails Core-IR.
from django.db import models

class Owner(models.Model):
    name = models.CharField(max_length=50)


class Part(models.Model):
    label = models.CharField(max_length=50)
    owner_ref = models.ForeignKey("Owner", on_delete=models.PROTECT, null=True, blank=True, related_name="+")


class Inspection(models.Model):
    result = models.CharField(max_length=80, null=True, blank=True)


class Attachment(models.Model):
    file_name = models.CharField(max_length=100)


class Asset(models.Model):
    name = models.CharField(max_length=50)
    parts = models.ManyToManyField("Part", blank=True, related_name="+")
    main_inspection = models.ForeignKey("Inspection", on_delete=models.CASCADE, related_name="+")
    optional_attachment = models.ForeignKey("Attachment", on_delete=models.CASCADE, null=True, blank=True, related_name="+")


class Document(models.Model):
    title = models.CharField(max_length=80)


class AssetDocument(models.Model):
    asset_role = models.ForeignKey("Asset", on_delete=models.PROTECT, null=True, blank=True, related_name="+")
    document_role = models.ForeignKey("Document", on_delete=models.PROTECT, null=True, blank=True, related_name="+")


