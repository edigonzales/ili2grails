# Generated from ili2grails Core-IR.
from django.contrib.gis.db import models

class AddressStatusChoices(models.TextChoices):
    ACTIVE = "active", "active"
    INACTIVE = "inactive", "inactive"
    PROPOSED = "proposed", "proposed"


class Address(models.Model):
    street = models.CharField(max_length=100)
    house_number = models.CharField(max_length=10, null=True, blank=True)
    postal_code = models.CharField(max_length=10)
    city = models.CharField(max_length=100)
    status = models.CharField(max_length=8, choices=AddressStatusChoices.choices, null=True, blank=True)
    position = models.GeometryField(null=True, blank=True)


class Person(models.Model):
    first_name = models.CharField(max_length=50)
    last_name = models.CharField(max_length=50)
    email = models.CharField(max_length=100, null=True, blank=True)
    phone_number = models.CharField(max_length=20, null=True, blank=True)
    birth_date = models.TextField(null=True, blank=True)


class PersonAddress(models.Model):
    address = models.ForeignKey("Address", on_delete=models.PROTECT, null=True, blank=True, related_name="+")
    person = models.ForeignKey("Person", on_delete=models.PROTECT, null=True, blank=True, related_name="+")


