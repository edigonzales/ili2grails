# Generated from ili2grails Core-IR.
from django.db import models

class AddressStatusChoices(models.TextChoices):
    ACTIVE = "active", "active"
    INACTIVE = "inactive", "inactive"
    PROPOSED = "proposed", "proposed"


class Address(models.Model):
    street = models.CharField(max_length=100, db_column="astreet")
    house_number = models.CharField(max_length=10, db_column="housenumber", null=True, blank=True)
    postal_code = models.CharField(max_length=10, db_column="postalcode")
    t_id = models.BigAutoField(primary_key=True, db_column="t_id")

    class Meta:
        db_table = "address"
        managed = False


class Person(models.Model):
    birth_date = models.DateField(db_column="birthdate", null=True, blank=True)
    first_name = models.CharField(max_length=50, db_column="firstname")
    last_name = models.CharField(max_length=50, db_column="lastname")
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


