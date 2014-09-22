#include "networking.h"
#include "imaging.h"

const bool tissuestack::imaging::TissueStackMincData::isRaw() const
{
	return false;
}

const bool tissuestack::imaging::TissueStackMincData::isColor() const
{
	return this->_is_color;
}

tissuestack::imaging::TissueStackMincData::TissueStackMincData(const std::string & filename) :
		tissuestack::imaging::TissueStackImageData(filename, tissuestack::imaging::FORMAT::MINC)
{
	// open file
	int result = miopen_volume(filename.c_str(), MI2_OPEN_RDWR, &this->_volume);
	if (result != MI_NOERROR)
		THROW_TS_EXCEPTION(
			tissuestack::common::TissueStackApplicationException,
			"Failed to open supposed MINC file!");

	// extract dimension number
	int numberOfDimensions = 0;
	result = miget_volume_dimension_count(
			this->_volume,
			MI_DIMCLASS_SPATIAL,
			MI_DIMATTR_ALL,
			&numberOfDimensions);
	if (result != MI_NOERROR)
		THROW_TS_EXCEPTION(
			tissuestack::common::TissueStackApplicationException,
			"Failed to get number of dimensions of MINC file!");

	if (numberOfDimensions > 3) // for now we assume anything greater to be the RGB channels
		this->_is_color = true;

	// extract some additional minc meta info
	result =
		miget_data_type(this->_volume, &this->_minc_type);
	if (result != MI_NOERROR)
		THROW_TS_EXCEPTION(
			tissuestack::common::TissueStackApplicationException,
			"Failed to extract data type of MINC file!");

	result =
			miget_data_type_size(this->_volume, &this->_minc_type_size);
	if (result != MI_NOERROR)
		THROW_TS_EXCEPTION(
			tissuestack::common::TissueStackApplicationException,
			"Failed to extract data type size of MINC file!");

	// get the volume dimensions
	midimhandle_t dimensions[numberOfDimensions > 3 ? 3 : numberOfDimensions];
	result =
		miget_volume_dimensions(
			this->_volume,
			MI_DIMCLASS_SPATIAL,
			MI_DIMATTR_ALL,
			MI_DIMORDER_FILE,
			numberOfDimensions,
			&dimensions[0]);
	if (result == MI_ERROR)
		THROW_TS_EXCEPTION(
			tissuestack::common::TissueStackApplicationException,
			"Failed to extract dimensions of MINC file!");

	// get size/start/step for each dimensions
	for (unsigned short i=0;i<numberOfDimensions;i++)
	{
		if (i > 2) // this is for non spatial, higher dimensions
			break;

		unsigned int size = 0;
		result = // SIZE
			miget_dimension_size(dimensions[i], &size);
		if (result != MI_NOERROR)
			THROW_TS_EXCEPTION(
				tissuestack::common::TissueStackApplicationException,
				"Failed to extract dimension size of MINC file!");

		double start = 0;
		result = // START
			miget_dimension_start(
				dimensions[i],
				MI_ORDER_FILE,
				&start);
		if (result != MI_NOERROR)
			THROW_TS_EXCEPTION(
				tissuestack::common::TissueStackApplicationException,
				"Failed to extract dimension start of MINC file!");

		double step = 0;
		result = // STEPS
			miget_dimension_separation(
				dimensions[i],
				MI_ORDER_FILE,
				&step);
		if (result != MI_NOERROR)
			THROW_TS_EXCEPTION(
				tissuestack::common::TissueStackApplicationException,
				"Failed to extract dimension step of MINC file!");

		char ** name = new char *[1];
		result = // STEPS
			miget_dimension_name(
				dimensions[i],
				name);
		if (result != MI_NOERROR)
		{
			delete [] name;
			THROW_TS_EXCEPTION(
				tissuestack::common::TissueStackApplicationException,
				"Failed to extract dimension name of MINC file!");
		}
		std::string sName = std::string(name[0], strlen(name[0]));
		free(name[0]);
		delete [] name;

		free(dimensions[i]);

		// create our dimension object and add it
		this->addDimension(
			new tissuestack::imaging::TissueStackDataDimension(
				sName,
				0, // bogus offset for MINC
				static_cast<unsigned long long int>(size),
				0)); // bogus slice size for MINC
		// add start and step
		this->addCoordinate(static_cast<float>(start));
		this->addStep(static_cast<float>(step));
	}

	// generate the raw header for conversion
	this->generateRawHeader();

	// further dimension info initialization
	this->initializeDimensions(true);
	this->initializeOffsetsForNonRawFiles();
}

const mitype_t tissuestack::imaging::TissueStackMincData::getMincType() const
{
	return this->_minc_type;
}

const misize_t tissuestack::imaging::TissueStackMincData::getMincTypeSize() const
{
	return this->_minc_type_size;
}

const mihandle_t tissuestack::imaging::TissueStackMincData::getMincHandle() const
{
	return this->_volume;
}


tissuestack::imaging::TissueStackMincData::~TissueStackMincData()
{
	if (this->_volume)
		miclose_volume(this->_volume);
}
