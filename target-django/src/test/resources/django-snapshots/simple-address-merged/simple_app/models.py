# Generated from ili2grails Core-IR.
from django.db import models

class AddressStatusChoices(models.TextChoices):
    ACTIVE = "active", "active"
    INACTIVE = "inactive", "inactive"
    PROPOSED = "proposed", "proposed"


class Address(models.Model):
    street = models.TextField(db_column="astreet", null=True, blank=True)
    house_number = models.TextField(db_column="housenumber", null=True, blank=True)
    postal_code = models.TextField(db_column="postalcode", null=True, blank=True)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "address"
        managed = False


class Person(models.Model):
    birth_date = models.TextField(db_column="birthdate", null=True, blank=True)
    first_name = models.TextField(db_column="firstname", null=True, blank=True)
    last_name = models.TextField(db_column="lastname", null=True, blank=True)
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "person"
        managed = False


class PersonAddress(models.Model):
    address = models.ForeignKey("Address", on_delete=models.PROTECT, db_column="address_id", null=True, blank=True, related_name="+")
    person = models.ForeignKey("Person", on_delete=models.PROTECT, db_column="person_id", null=True, blank=True, related_name="+")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "personaddress"
        managed = False


